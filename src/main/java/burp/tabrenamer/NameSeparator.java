package burp.tabrenamer;

import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

/**
 * How the extension joins the pieces it assembles into a tab name — the parts of a
 * body-derived name ({@code user}+{@code bob}), the multiple selections staged for one name,
 * and the character illegal input is replaced with.
 *
 * <p>Two distinct roles, which only coincide for some options:
 * <ul>
 *   <li>{@link #joiner()} goes <em>between name parts</em>. {@link #PIPE} pads itself with
 *       spaces here, because {@code POST | users | admin} is far easier to read than
 *       {@code POST|users|admin}.</li>
 *   <li>{@link #filler()} replaces runs of characters that aren't legal in a tab name. For
 *       {@link #PIPE} that's a space, not a pipe: a pipe implies structure, and stamping one
 *       over every stray punctuation mark inside a single token would invent boundaries that
 *       aren't there ({@code session_id | abc123} for what is really one value).</li>
 * </ul>
 *
 * <p>Deliberately a small closed set rather than free text: the value is spliced into a
 * regex character class in {@link TabNameGenerator}, so an arbitrary string would need
 * escaping to stay safe, and a separator that is itself stripped as an illegal character
 * (say {@code "/"}) would produce nonsense names.
 *
 * <p>This governs separators <em>the extension inserts</em>. Characters already present in the
 * source text are left alone: with {@link #SPACE} selected, a path segment {@code user-profile}
 * still comes through as {@code user-profile}. Two things are also deliberately exempt — the
 * number appended to a duplicate name (always {@code name (2)}, see
 * {@link TabNameGenerator#withOrdinalSuffix}) and the divider in the
 * {@link NamingFormat#METHOD_AND_PATH} format (always {@code " | "}).
 *
 * <p>Labels are plain ASCII words on purpose: they double as the value Burp persists and hands
 * back, so anything decorative in them becomes part of a string that has to survive a
 * round-trip through Burp's settings store.
 */
public enum NameSeparator {

    HYPHEN("Hyphen", "-", "-", ""),
    SPACE("Space", " ", " ", " "),
    UNDERSCORE("Underscore", "_", "_", ""),
    PIPE("Pipe", " | ", " ", " |");

    private final String label;
    private final String joiner;
    private final String filler;
    private final String extraAllowedChars;

    NameSeparator(String label, String joiner, String filler, String extraAllowedChars) {
        this.label = label;
        this.joiner = joiner;
        this.filler = filler;
        this.extraAllowedChars = extraAllowedChars;
    }

    /** The option shown in Burp's settings UI; also the value Burp persists. */
    public String label() {
        return label;
    }

    /** Inserted between name parts. */
    public String joiner() {
        return joiner;
    }

    /** Replaces a run of characters that can't appear in a tab name. */
    public String filler() {
        return filler;
    }

    /**
     * Characters this separator must add to the "keep as-is" set when sanitizing, so its own
     * joiner and filler don't immediately get replaced. {@code -} and {@code _} are already
     * legal tab-name characters, so only the space- and pipe-based options need anything.
     */
    String extraAllowedChars() {
        return extraAllowedChars;
    }

    public static List<String> labels() {
        return Stream.of(values()).map(NameSeparator::label).toList();
    }

    /** @see SettingOption#resolve */
    public static Optional<NameSeparator> fromSetting(String value) {
        return SettingOption.resolve(values(), NameSeparator::label, value);
    }
}
