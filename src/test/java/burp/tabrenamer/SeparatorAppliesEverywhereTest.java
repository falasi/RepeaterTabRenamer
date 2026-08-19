package burp.tabrenamer;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Regression tests for the bug where the separator preference appeared to apply to automatic
 * naming but not to names built from selections.
 *
 * <p>The defect was never in any one handler: every route already went through
 * {@link TabNameGenerator}, but the preference was read back from Burp as a decorated display
 * label that no longer matched, so the lookup silently fell back to the default. Names built
 * purely from a path segment insert no separator at all, so those looked correct and hid it.
 *
 * <p>These tests pin both halves: that a resolved separator reaches every naming route, and
 * that the values Burp can hand back resolve at all.
 */
class SeparatorAppliesEverywhereTest {

    /** Stands in for the settings panel, so a mid-session change can be simulated. */
    private NameSeparator current = NameSeparator.HYPHEN;
    private final TabNameGenerator generator = new TabNameGenerator(() -> current);

    private static final List<String> STAGED_PARTS = List.of("POST", "users", "admin");

    @Test
    void stagedPartsUseTheConfiguredSeparator() {
        current = NameSeparator.HYPHEN;
        assertEquals("POST-users-admin", generator.joinParts(STAGED_PARTS));

        current = NameSeparator.SPACE;
        assertEquals("POST users admin", generator.joinParts(STAGED_PARTS));

        current = NameSeparator.UNDERSCORE;
        assertEquals("POST_users_admin", generator.joinParts(STAGED_PARTS));
    }

    @Test
    void twoStagedPartsUseTheConfiguredSeparator() {
        List<String> two = List.of("POST", "users");
        current = NameSeparator.HYPHEN;
        assertEquals("POST-users", generator.joinParts(two));

        current = NameSeparator.SPACE;
        assertEquals("POST users", generator.joinParts(two));

        current = NameSeparator.UNDERSCORE;
        assertEquals("POST_users", generator.joinParts(two));
    }

    @Test
    void automaticBodyNamingUsesTheConfiguredSeparator() {
        current = NameSeparator.HYPHEN;
        assertEquals("user-bob", generator.generate("POST", "/api", "URL_ENCODED", "user=bob", "example.com"));

        current = NameSeparator.SPACE;
        assertEquals("user bob", generator.generate("POST", "/api", "URL_ENCODED", "user=bob", "example.com"));

        current = NameSeparator.UNDERSCORE;
        assertEquals("user_bob", generator.generate("POST", "/api", "URL_ENCODED", "user=bob", "example.com"));
    }

    @Test
    void directSelectionNamingUsesTheConfiguredSeparator() {
        current = NameSeparator.HYPHEN;
        assertEquals("session_id-abc123", generator.sanitizeForTabName("session_id: abc123"));

        current = NameSeparator.SPACE;
        assertEquals("session_id abc123", generator.sanitizeForTabName("session_id: abc123"));

        current = NameSeparator.UNDERSCORE;
        assertEquals("session_id_abc123", generator.sanitizeForTabName("session_id: abc123"));
    }

    @Test
    void changingTheSeparatorTakesEffectOnTheNextNameWithNoReload() {
        // Same generator instance throughout: the preference is read per call, never cached at
        // construction, which is what makes a mid-session change apply without reloading.
        current = NameSeparator.HYPHEN;
        assertEquals("POST-users-admin", generator.joinParts(STAGED_PARTS));

        current = NameSeparator.SPACE;
        assertEquals("POST users admin", generator.joinParts(STAGED_PARTS));
        assertEquals("user bob", generator.generate("POST", "/api", "URL_ENCODED", "user=bob", "example.com"));

        current = NameSeparator.HYPHEN;
        assertEquals("POST-users-admin", generator.joinParts(STAGED_PARTS));
    }

    @Test
    void duplicateNumberingStaysBracketedUnderEverySeparator() {
        for (NameSeparator separator : NameSeparator.values()) {
            current = separator;
            String base = "admin";
            assertEquals("admin (2)", generator.uniqueAmong(base, List.of(base)),
                    "duplicate suffix must not follow the " + separator + " preference");
        }
    }

    @Test
    void everyOfferedOptionResolvesBackToItsSeparator() {
        // The panel offers exactly these strings and hands one of them back; if a label can't
        // round-trip, the whole preference silently stops working.
        for (NameSeparator separator : NameSeparator.values()) {
            assertEquals(separator, NameSeparator.fromSetting(separator.label()).orElseThrow(),
                    "label \"" + separator.label() + "\" must resolve back to " + separator);
        }
        assertEquals(NameSeparator.labels().size(), NameSeparator.values().length);
    }

    @Test
    void labelsAreAsciiSoTheySurviveARoundTripThroughBurpsSettingsStore() {
        for (String label : NameSeparator.labels()) {
            assertEquals(label, new String(label.getBytes(java.nio.charset.StandardCharsets.US_ASCII),
                    java.nio.charset.StandardCharsets.US_ASCII), "label must be pure ASCII: " + label);
        }
    }

    @Test
    void decoratedLabelsFromEarlierBuildsStillResolve() {
        // Anyone upgrading has "Space — api users" (or similar) already persisted.
        assertEquals(NameSeparator.SPACE, NameSeparator.fromSetting("Space — api users").orElseThrow());
        assertEquals(NameSeparator.UNDERSCORE, NameSeparator.fromSetting("Underscore — api_users").orElseThrow());
        assertEquals(NameSeparator.HYPHEN, NameSeparator.fromSetting("  hyphen  ").orElseThrow());
    }

    @Test
    void unrecognisedValuesAreReportedRatherThanQuietlyDefaulted() {
        // Empty means "we don't know", which the caller logs; it must not masquerade as a
        // deliberate choice of the default.
        assertEquals(java.util.Optional.empty(), NameSeparator.fromSetting(null));
        assertEquals(java.util.Optional.empty(), NameSeparator.fromSetting("   "));
        assertEquals(java.util.Optional.empty(), NameSeparator.fromSetting("Tilde"));
    }
}
