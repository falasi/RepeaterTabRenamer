package burp.tabrenamer;

import java.util.List;

/**
 * Every hotkey this extension asks Burp for, and the order it falls back through — kept in one
 * place so a combo that turns out to clash with a popular BApp can be changed without hunting
 * through registration code.
 *
 * <p>Choosing defaults is guesswork by necessity: Burp exposes no way to enumerate what's
 * already bound, and other extensions claim combos at their own load time, so a combo that is
 * free on one install is taken on another. Ctrl+Alt+S, the original "send to Repeater" default,
 * turned out to clash with Hackvertor. The response is a chain rather than a better guess —
 * whichever combo wins is what the load banner prints.
 *
 * <p>Within a chain: the first entry is the mnemonic one, the rest trade mnemonics for
 * unlikelihood of collision. Three-modifier combos come in both orderings because Burp only
 * documents its hotkey strings as "the same format as within Burp's Settings" and doesn't say
 * whether the parser is order-sensitive; if the first ordering isn't understood, the next
 * candidate covers it at no cost.
 */
final class HotKeyPlan {

    private HotKeyPlan() {
    }

    /** Rename the active Repeater tab. Ctrl+Alt+R is reported working, so it stays first. */
    static final List<String> RENAME = List.of(
            "Ctrl+Alt+R",
            "Ctrl+Shift+R",
            "Ctrl+Alt+Shift+R",
            "Ctrl+Alt+E");

    /**
     * Send to Repeater with automatic naming.
     *
     * <p>Ctrl+Alt+S is gone: Hackvertor claims it, and Hackvertor is common enough that keeping
     * it as the default would leave many installs falling back on every load. The replacement is
     * the rename key plus Shift — mnemonically "the same action, from somewhere else" — and
     * three modifiers make an accidental clash unlikely.
     */
    static final List<String> SEND = List.of(
            "Ctrl+Alt+Shift+R",
            "Ctrl+Shift+Alt+R",
            "Ctrl+Alt+G",
            "Ctrl+Shift+G",
            "Ctrl+Alt+Q");

    /** Stage a selection as name part {@code slot}. Digits are untouched by stock Burp. */
    static List<String> part(int slot) {
        return List.of(
                "Ctrl+Alt+" + slot,
                "Ctrl+Shift+" + slot,
                "Ctrl+Alt+Shift+" + slot);
    }
}
