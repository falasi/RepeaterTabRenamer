package burp.tabrenamer;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * How registration outcomes become the rows the user reads at load — in particular that the
 * three part-staging hotkeys only collapse into "Ctrl+Alt+1/2/3" when that is literally true.
 */
class BannerShortcutRowsTest {

    private static HotKeyRegistrar.Outcome registered(String combo) {
        return new HotKeyRegistrar.Outcome(HotKeyRegistrar.Status.REGISTERED, combo);
    }

    private static final HotKeyRegistrar.Outcome UNAVAILABLE =
            new HotKeyRegistrar.Outcome(HotKeyRegistrar.Status.FAILED, null);

    private List<String> combosOf(List<UsageBanner.Shortcut> shortcuts) {
        return shortcuts.stream().map(UsageBanner.Shortcut::combo).toList();
    }

    @Test
    void collapsesThePartHotkeysWhenTheyRegisteredAsAConsistentSet() {
        List<UsageBanner.Shortcut> rows = UsageBanner.rows(
                registered("Ctrl+Alt+R"),
                List.of(registered("Ctrl+Alt+1"), registered("Ctrl+Alt+2"), registered("Ctrl+Alt+3")),
                registered("Ctrl+Alt+Shift+R"));
        assertEquals(List.of("Ctrl+Alt+R", "Ctrl+Alt+1/2/3", "Ctrl+Alt+Shift+R"), combosOf(rows));
    }

    @Test
    void listsThePartHotkeysSeparatelyWhenOneOfThemFellBack() {
        // Collapsing here would advertise Ctrl+Alt+2, which is bound to something else.
        List<UsageBanner.Shortcut> rows = UsageBanner.rows(
                registered("Ctrl+Alt+R"),
                List.of(registered("Ctrl+Alt+1"), registered("Ctrl+Shift+2"), registered("Ctrl+Alt+3")),
                registered("Ctrl+Alt+Shift+R"));
        assertEquals(List.of("Ctrl+Alt+R", "Ctrl+Alt+1", "Ctrl+Shift+2", "Ctrl+Alt+3", "Ctrl+Alt+Shift+R"),
                combosOf(rows));
    }

    @Test
    void listsThePartHotkeysSeparatelyWhenOneCouldNotRegisterAtAll() {
        List<UsageBanner.Shortcut> rows = UsageBanner.rows(
                registered("Ctrl+Alt+R"),
                List.of(registered("Ctrl+Alt+1"), UNAVAILABLE, registered("Ctrl+Alt+3")),
                registered("Ctrl+Alt+Shift+R"));
        assertEquals(List.of("Ctrl+Alt+R", "Ctrl+Alt+1", UsageBanner.UNAVAILABLE, "Ctrl+Alt+3", "Ctrl+Alt+Shift+R"),
                combosOf(rows));
    }

    @Test
    void showsUnavailableRatherThanAComboForAHotkeyThatNeverRegistered() {
        List<UsageBanner.Shortcut> rows = UsageBanner.rows(
                registered("Ctrl+Alt+R"),
                List.of(registered("Ctrl+Alt+1"), registered("Ctrl+Alt+2"), registered("Ctrl+Alt+3")),
                UNAVAILABLE);
        assertEquals(UsageBanner.UNAVAILABLE, combosOf(rows).get(2));
    }
}
