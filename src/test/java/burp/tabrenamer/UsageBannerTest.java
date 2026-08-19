package burp.tabrenamer;

import burp.tabrenamer.UsageBanner.Shortcut;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class UsageBannerTest {

    private static final List<Shortcut> REGISTERED = List.of(
            new Shortcut("Ctrl+Alt+R", "Rename from selection / staged parts"),
            new Shortcut("Ctrl+Alt+1/2/3", "Stage name parts"),
            new Shortcut("Ctrl+Alt+Shift+R", "Send to Repeater with automatic naming"));

    @Test
    void showsTheShortcutsThatActuallyRegistered() {
        String banner = UsageBanner.render("1.2.0", REGISTERED);
        assertTrue(banner.contains("Ctrl+Alt+R"));
        assertTrue(banner.contains("Ctrl+Alt+1/2/3"));
        assertTrue(banner.contains("Ctrl+Alt+Shift+R"));
    }

    @Test
    void showsAFallbackComboRatherThanThePreferredOne() {
        String banner = UsageBanner.render("1.2.0",
                List.of(new Shortcut("Ctrl+Shift+R", "Rename from selection / staged parts")));
        assertTrue(banner.contains("Ctrl+Shift+R"));
        assertFalse(banner.contains("Ctrl+Alt+R"), "must not advertise a combo that never registered");
    }

    @Test
    void marksAnUnusableHotkeyAsUnavailable() {
        String banner = UsageBanner.render("1.2.0",
                List.of(new Shortcut(UsageBanner.UNAVAILABLE, "Send to Repeater with automatic naming")));
        assertTrue(banner.contains("(unavailable)"));
    }

    @Test
    void includesTheVersionWhenKnownAndOmitsItOtherwise() {
        assertTrue(UsageBanner.render("1.2.0", REGISTERED).startsWith("Repeater Tab Renamer v1.2.0 enabled"));
        assertTrue(UsageBanner.render(null, REGISTERED).startsWith("Repeater Tab Renamer enabled"));
        assertTrue(UsageBanner.render("  ", REGISTERED).startsWith("Repeater Tab Renamer enabled"));
    }

    @Test
    void pointsAtBothSettingsLocations() {
        String banner = UsageBanner.render("1.2.0", REGISTERED);
        assertTrue(banner.contains("Separator: Settings > Extensions > Repeater Tab Renamer"));
        assertTrue(banner.contains("Settings > Hotkeys"));
    }

    @Test
    void staysCompact() {
        // A load-time reminder, not documentation: the explanations live in the README.
        String banner = UsageBanner.render("1.2.0", REGISTERED);
        assertEquals(8, banner.lines().count());
        assertFalse(banner.contains("admin (2)"), "duplicate examples belong in the README");
        assertFalse(banner.contains("Automatic naming"), "fallback naming explanation belongs in the README");
        assertFalse(banner.contains("Multi-part naming"), "multi-line instructions belong in the README");
    }

    @Test
    void alignsTheShortcutColumnAcrossMixedLabelWidths() {
        String banner = UsageBanner.render("1.2.0",
                List.of(new Shortcut("Ctrl+Alt+R", "Rename."), new Shortcut(UsageBanner.UNAVAILABLE, "Send.")));
        assertTrue(banner.contains("Ctrl+Alt+R      Rename."));
        assertTrue(banner.contains("(unavailable)   Send."));
    }
}
