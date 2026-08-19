package burp.tabrenamer;

import java.util.ArrayList;
import java.util.List;

/**
 * Renders the short reminder printed to the extension's Output tab at load.
 *
 * <p>Deliberately terse — a glanceable list of what's bound, not documentation. The README
 * carries the explanations; repeating them in a log pane the user scrolls past once is noise.
 * Everything else this extension logs is either the result of an explicit user action or an
 * error, so the Output tab stays quiet in normal use.
 *
 * <p>Separated from {@link RepeaterTabRenamerExtension} so the rule that matters most here can
 * be asserted in tests without a running Burp: the banner shows the shortcut each hotkey
 * <em>actually</em> registered with, never the one that was requested. Combos are claimed on a
 * first-come basis and ours may lose, so printing the preferred combo would tell the user to
 * press a key that does nothing.
 */
final class UsageBanner {

    /**
     * @param combo what to show in the shortcut column: the combo Burp accepted, or
     *              {@code (unavailable)} for a hotkey that isn't usable at all
     * @param usage a few words on what it does
     */
    record Shortcut(String combo, String usage) {
    }

    static final String UNAVAILABLE = "(unavailable)";

    private static final String RENAME_USAGE = "Rename from selection / staged parts";
    private static final String PARTS_USAGE = "Stage name parts";
    private static final String SEND_USAGE = "Send to Repeater with automatic naming";

    private UsageBanner() {
    }

    /**
     * @param version may be null when the version can't be read (e.g. running from classes
     *                rather than the built jar), in which case it's simply left off
     */
    static String render(String version, List<Shortcut> shortcuts) {
        StringBuilder banner = new StringBuilder("Repeater Tab Renamer ");
        if (version != null && !version.isBlank()) {
            banner.append('v').append(version.trim()).append(' ');
        }
        banner.append("enabled\n\n");

        int comboWidth = shortcuts.stream().mapToInt(s -> s.combo().length()).max().orElse(0);
        for (Shortcut shortcut : shortcuts) {
            banner.append(padRight(shortcut.combo(), comboWidth)).append("   ").append(shortcut.usage()).append('\n');
        }

        banner.append("\nSeparator: Settings > Extensions > Repeater Tab Renamer\n");
        banner.append("Hotkeys can be changed under Settings > Hotkeys.");
        return banner.toString();
    }

    /**
     * Turns registration outcomes into rows. The three part-staging hotkeys collapse into a
     * single "Ctrl+Alt+1/2/3" row when they registered as a consistent set; if any of them fell
     * back or failed, they're listed one per line rather than printed as a range that would send
     * the user to a key bound to something else.
     */
    static List<Shortcut> rows(HotKeyRegistrar.Outcome rename,
                               List<HotKeyRegistrar.Outcome> parts,
                               HotKeyRegistrar.Outcome send) {
        List<Shortcut> rows = new ArrayList<>();
        rows.add(new Shortcut(comboLabel(rename), RENAME_USAGE));

        String collapsed = collapsedPartCombos(parts);
        if (collapsed != null) {
            rows.add(new Shortcut(collapsed, PARTS_USAGE));
        } else {
            for (int i = 0; i < parts.size(); i++) {
                rows.add(new Shortcut(comboLabel(parts.get(i)), "Stage name part " + (i + 1)));
            }
        }

        rows.add(new Shortcut(comboLabel(send), SEND_USAGE));
        return rows;
    }

    /**
     * "Ctrl+Alt+1/2/3" when every part hotkey registered with the same prefix and its own slot
     * digit, otherwise null.
     */
    private static String collapsedPartCombos(List<HotKeyRegistrar.Outcome> parts) {
        String prefix = null;
        for (int i = 0; i < parts.size(); i++) {
            HotKeyRegistrar.Outcome outcome = parts.get(i);
            String slot = String.valueOf(i + 1);
            if (!outcome.isRegistered() || !outcome.combo().endsWith(slot)) {
                return null;
            }
            String comboPrefix = outcome.combo().substring(0, outcome.combo().length() - slot.length());
            if (prefix == null) {
                prefix = comboPrefix;
            } else if (!prefix.equals(comboPrefix)) {
                return null;
            }
        }
        if (prefix == null) {
            return null;
        }
        StringBuilder collapsed = new StringBuilder(prefix);
        for (int i = 1; i <= parts.size(); i++) {
            collapsed.append(i == 1 ? "" : "/").append(i);
        }
        return collapsed.toString();
    }

    private static String comboLabel(HotKeyRegistrar.Outcome outcome) {
        return outcome.isRegistered() ? outcome.combo() : UNAVAILABLE;
    }

    private static String padRight(String value, int width) {
        return value.length() >= width ? value : value + " ".repeat(width - value.length());
    }
}
