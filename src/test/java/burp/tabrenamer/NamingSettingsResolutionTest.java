package burp.tabrenamer;

import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * How a value read back from Burp's settings panel becomes a preference.
 *
 * <p>Covers the cases behind the "reads null" warning: Burp returns nothing for a setting the
 * user has never touched, which is the normal state after a fresh install or after an upgrade
 * adds a setting — it must resolve to a working default with no warning and no trip to the
 * settings dialog.
 *
 * <p>{@link NamingSettings} itself can't be exercised without a live Burp, so this pins the
 * resolution rules it delegates to; {@code NamingSettings.read} adds only the blank check and
 * the one-time log around them.
 */
class NamingSettingsResolutionTest {

    /** Mirrors NamingSettings.read: blank or unrecognised falls back, anything known resolves. */
    private static <T> T resolve(Optional<T> resolved, T fallback) {
        return resolved.orElse(fallback);
    }

    @Test
    void nullStoredValueYieldsTheDefaultWithoutUserIntervention() {
        assertTrue(NameSeparator.fromSetting(null).isEmpty());
        assertEquals(NameSeparator.HYPHEN, resolve(NameSeparator.fromSetting(null), NamingConfig.DEFAULT.separator()));

        assertTrue(NamingFormat.fromSetting(null).isEmpty());
        assertEquals(NamingFormat.METHOD_AND_PATH,
                resolve(NamingFormat.fromSetting(null), NamingConfig.DEFAULT.format()));
    }

    @Test
    void emptyOrBlankStoredValueYieldsTheDefault() {
        for (String blank : new String[]{"", "   ", "\t"}) {
            assertEquals(NameSeparator.HYPHEN,
                    resolve(NameSeparator.fromSetting(blank), NamingConfig.DEFAULT.separator()));
            assertEquals(NamingFormat.METHOD_AND_PATH,
                    resolve(NamingFormat.fromSetting(blank), NamingConfig.DEFAULT.format()));
        }
    }

    @Test
    void invalidStoredValueFallsBackSafely() {
        assertEquals(NameSeparator.HYPHEN,
                resolve(NameSeparator.fromSetting("Semicolon"), NamingConfig.DEFAULT.separator()));
        assertEquals(NamingFormat.METHOD_AND_PATH,
                resolve(NamingFormat.fromSetting("Whatever"), NamingConfig.DEFAULT.format()));
    }

    @Test
    void validOldValuesFromEarlierBuildsStillResolve() {
        // v1.1.0 stored decorated labels; v1.2.0 stored plain ones. Both must survive.
        assertEquals(NameSeparator.SPACE, NameSeparator.fromSetting("Space — api users").orElseThrow());
        assertEquals(NameSeparator.HYPHEN, NameSeparator.fromSetting("Hyphen — api-users").orElseThrow());
        assertEquals(NameSeparator.UNDERSCORE, NameSeparator.fromSetting("Underscore").orElseThrow());
    }

    @Test
    void hyphenResolves() {
        assertEquals(NameSeparator.HYPHEN, NameSeparator.fromSetting("Hyphen").orElseThrow());
        assertEquals("-", NameSeparator.HYPHEN.joiner());
    }

    @Test
    void spaceResolves() {
        assertEquals(NameSeparator.SPACE, NameSeparator.fromSetting("Space").orElseThrow());
        assertEquals(" ", NameSeparator.SPACE.joiner());
    }

    @Test
    void underscoreResolves() {
        assertEquals(NameSeparator.UNDERSCORE, NameSeparator.fromSetting("Underscore").orElseThrow());
        assertEquals("_", NameSeparator.UNDERSCORE.joiner());
    }

    @Test
    void pipeResolves() {
        assertEquals(NameSeparator.PIPE, NameSeparator.fromSetting("Pipe").orElseThrow());
        assertEquals(" | ", NameSeparator.PIPE.joiner());
    }

    @Test
    void caseAndPaddingDoNotMatter() {
        assertEquals(NameSeparator.PIPE, NameSeparator.fromSetting("  pipe  ").orElseThrow());
        assertEquals(NamingFormat.METHOD_AND_PATH, NamingFormat.fromSetting("METHOD AND PATH").orElseThrow());
    }

    @Test
    void settingNamesAreStableBecauseTheyAreThePersistenceKeys() {
        // Renaming either of these silently discards every user's saved choice.
        assertEquals("Name separator", NamingSettings.SEPARATOR_SETTING);
        assertEquals("Automatic naming format", NamingSettings.FORMAT_SETTING);
    }

    @Test
    void aNullConfigDegradesToWorkingDefaults() {
        assertEquals(NameSeparator.HYPHEN, new NamingConfig(null, null).separator());
        assertEquals(NamingFormat.METHOD_AND_PATH, new NamingConfig(null, null).format());
    }
}
