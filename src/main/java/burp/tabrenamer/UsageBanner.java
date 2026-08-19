package burp.tabrenamer;

import java.util.List;

/**
 * Renders the one-off "here's how to use this" message printed to the extension's Output tab at
 * load, so the extension is usable without opening the README. Everything else this extension
 * logs is either the result of an explicit user action or an error, so the Output tab stays
 * quiet during normal use.
 *
 * <p>Separated from {@link RepeaterTabRenamerExtension} so the text can be asserted in tests
 * without a running Burp — in particular the rule that matters most here: the banner shows the
 * shortcut each hotkey <em>actually</em> registered with, never the one that was requested. A
 * combo already claimed elsewhere falls back to its alternative, and printing the first choice
 * would tell the user to press a key that does nothing.
 */
final class UsageBanner {

    /**
     * @param combo what to show in the shortcut column: the combo Burp accepted, or a marker for
     *              a hotkey that isn't usable at all
     * @param usage what the shortcut does; may contain newlines for continuation lines
     */
    record Shortcut(String combo, String usage) {
    }

    private UsageBanner() {
    }

    static String render(List<Shortcut> shortcuts, boolean anyUnsupported, boolean anyFailed) {
        StringBuilder banner = new StringBuilder("Repeater Tab Renamer enabled\n");

        banner.append("\nHotkeys (as registered just now - rebindable in Settings > Hotkeys):\n");
        int comboWidth = shortcuts.stream().mapToInt(s -> s.combo().length()).max().orElse(0);
        String indent = " ".repeat(2 + comboWidth + 2);
        for (Shortcut shortcut : shortcuts) {
            String[] lines = shortcut.usage().split("\n");
            banner.append("  ").append(padRight(shortcut.combo(), comboWidth)).append("  ").append(lines[0]).append('\n');
            for (int i = 1; i < lines.length; i++) {
                banner.append(indent).append(lines[i]).append('\n');
            }
        }

        banner.append("\nMulti-part naming:\n")
                .append("  Select text -> part 1 hotkey, select another -> part 2 hotkey, then the\n")
                .append("  rename hotkey to combine them into one name. Parts belong to the tab you\n")
                .append("  staged them in, and are cleared once used.\n");

        banner.append("\nAutomatic naming:\n")
                .append("  Sending from Repeater names tabs that still look auto-generated (1, 2, Untitled)\n")
                .append("  after the request path, a request body field, or the host. A name you set\n")
                .append("  yourself is never overwritten.\n");

        banner.append("\nDuplicate names are numbered automatically:\n")
                .append("  admin, admin (2), admin (3)\n");

        banner.append("\nName separator (hyphen / space / underscore):\n")
                .append("  Settings > Extensions > Repeater Tab Renamer\n");

        banner.append("\nThe rename action is also on the right-click menu, under Extensions.");

        if (anyUnsupported) {
            banner.append("\n\nHotkeys marked unavailable need a Burp release shipping montoya-api 2025.12 or\n")
                    .append("later; automatic naming and the right-click menu item work regardless.");
        }
        if (anyFailed) {
            banner.append("\n\nA hotkey marked unregistered had every candidate combo already bound to something\n")
                    .append("else. See the Errors tab, or bind it by hand via Settings > Hotkeys.");
        }

        return banner.toString();
    }

    private static String padRight(String value, int width) {
        return value.length() >= width ? value : value + " ".repeat(width - value.length());
    }
}
