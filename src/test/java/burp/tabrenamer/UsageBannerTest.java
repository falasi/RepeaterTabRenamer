package burp.tabrenamer;

import burp.tabrenamer.UsageBanner.Shortcut;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class UsageBannerTest {

    private static final List<Shortcut> FIRST_CHOICE = List.of(
            new Shortcut("Ctrl+Alt+R", "Rename the current Repeater tab from the selected text."),
            new Shortcut("Ctrl+Alt+1", "Store the Repeater selection as name part 1"),
            new Shortcut("Ctrl+Alt+S", "Send to Repeater with automatic naming."));

    @Test
    void showsTheShortcutsThatActuallyRegistered() {
        String banner = UsageBanner.render(FIRST_CHOICE, false, false);
        assertTrue(banner.contains("Ctrl+Alt+R"));
        assertTrue(banner.contains("Ctrl+Alt+1"));
        assertTrue(banner.contains("Ctrl+Alt+S"));
    }

    @Test
    void showsAFallbackComboRatherThanTheRequestedOne() {
        // Ctrl+Alt+R was taken, so registration fell back — the banner must not tell the user to
        // press a key that does nothing.
        List<Shortcut> fellBack = List.of(
                new Shortcut("Ctrl+Shift+R", "Rename the current Repeater tab from the selected text."));
        String banner = UsageBanner.render(fellBack, false, false);
        assertTrue(banner.contains("Ctrl+Shift+R"));
        assertFalse(banner.contains("Ctrl+Alt+R"));
    }

    @Test
    void marksHotkeysThatCouldNotRegisterAndExplainsWhy() {
        String unsupported = UsageBanner.render(
                List.of(new Shortcut("(unavailable)", "Rename the current Repeater tab.")), true, false);
        assertTrue(unsupported.contains("(unavailable)"));
        assertTrue(unsupported.contains("montoya-api 2025.12"));

        String failed = UsageBanner.render(
                List.of(new Shortcut("(unregistered)", "Rename the current Repeater tab.")), false, true);
        assertTrue(failed.contains("(unregistered)"));
        assertTrue(failed.contains("already bound to something"));
    }

    @Test
    void omitsTheFallbackNotesWhenEverythingRegistered() {
        String banner = UsageBanner.render(FIRST_CHOICE, false, false);
        assertFalse(banner.contains("montoya-api 2025.12"));
        assertFalse(banner.contains("already bound to something"));
    }

    @Test
    void coversEveryFeatureAUserWouldOtherwiseNeedTheReadmeFor() {
        String banner = UsageBanner.render(FIRST_CHOICE, false, false);
        assertTrue(banner.contains("Repeater Tab Renamer enabled"));
        assertTrue(banner.contains("Multi-part naming"));
        assertTrue(banner.contains("Automatic naming"));
        assertTrue(banner.contains("admin (2)"), "duplicate example must match the implemented format");
        assertTrue(banner.contains("Settings > Extensions > Repeater Tab Renamer"));
        assertFalse(banner.contains("admin-2"), "must not advertise the old suffix format");
    }

    @Test
    void staysShortEnoughToReadInTheOutputTab() {
        String banner = UsageBanner.render(FIRST_CHOICE, false, false);
        assertTrue(banner.lines().count() < 35, "banner should stay a concise reference, not a manual");
    }

    @Test
    void alignsTheShortcutColumnAcrossMixedLabelWidths() {
        String banner = UsageBanner.render(
                List.of(new Shortcut("Ctrl+Alt+R", "Rename."), new Shortcut("(unregistered)", "Send.")),
                false, true);
        assertTrue(banner.contains("  Ctrl+Alt+R      Rename."));
        assertTrue(banner.contains("  (unregistered)  Send."));
    }
}
