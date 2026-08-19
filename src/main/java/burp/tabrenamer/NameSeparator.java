package burp.tabrenamer;

import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.stream.Stream;

/**
 * How the extension joins the pieces it assembles into a tab name — the parts of a
 * body-derived name ({@code user}+{@code bob}), the multiple selections staged for one name,
 * and the character illegal input is replaced with.
 *
 * <p>Deliberately a small closed set rather than free text: the value is spliced into a
 * regex character class in {@link TabNameGenerator}, so an arbitrary string would need
 * escaping to stay safe, and a separator that is itself stripped as an illegal character
 * (say {@code "/"}) would produce nonsense names.
 *
 * <p>Note this governs separators <em>this extension inserts</em>. Characters already
 * present in the source text are left alone: with {@link #SPACE} selected, a path segment
 * {@code user-profile} still comes through as {@code user-profile}, not {@code user profile}.
 * The number appended to a duplicate name is not affected either — see
 * {@link TabNameGenerator#withOrdinalSuffix}.
 *
 * <p>Labels are plain single words on purpose. They double as the value Burp persists and hands
 * back, so anything decorative in them (an example, a dash, a non-ASCII character) becomes part
 * of a string that has to survive a round-trip through Burp's settings store and match exactly.
 * An earlier version used decorated labels and silently fell back to {@link #HYPHEN} when the
 * match failed, which made the whole preference look like it did nothing. Examples now live in
 * the setting's description instead, and {@link #fromSetting} matches leniently.
 */
public enum NameSeparator {

    HYPHEN("Hyphen", "-", ""),
    SPACE("Space", " ", " "),
    UNDERSCORE("Underscore", "_", "");

    private final String label;
    private final String value;
    private final String extraAllowedChars;

    NameSeparator(String label, String value, String extraAllowedChars) {
        this.label = label;
        this.value = value;
        this.extraAllowedChars = extraAllowedChars;
    }

    /** The option shown in Burp's settings UI; also the value Burp persists. */
    public String label() {
        return label;
    }

    /** The string actually inserted between name parts. */
    public String value() {
        return value;
    }

    /**
     * Characters this separator must add to the "keep as-is" set when sanitizing, so the
     * separator doesn't immediately get replaced by itself. Only {@link #SPACE} needs it —
     * {@code -} and {@code _} are already legal tab-name characters.
     */
    String extraAllowedChars() {
        return extraAllowedChars;
    }

    public static List<String> labels() {
        return Stream.of(values()).map(NameSeparator::label).toList();
    }

    /**
     * Resolves the value Burp gives back for the setting.
     *
     * <p>Lenient by design: an exact match is tried first, then a case-insensitive one, then a
     * containment check. The last of those matters for anyone upgrading from a build whose
     * labels carried an example ("Space — api users") — their stored value still resolves to
     * {@link #SPACE} instead of silently reverting to the default.
     *
     * @return empty if the value means nothing to us, so the caller can report it rather than
     *         quietly substituting a default
     */
    public static Optional<NameSeparator> fromSetting(String value) {
        if (value == null || value.isBlank()) {
            return Optional.empty();
        }
        String normalized = value.trim().toLowerCase(Locale.ROOT);
        return Stream.of(values())
                .filter(separator -> {
                    String label = separator.label.toLowerCase(Locale.ROOT);
                    return normalized.equals(label) || normalized.contains(label);
                })
                .findFirst();
    }
}
