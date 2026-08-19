package burp.tabrenamer;

import java.util.List;
import java.util.stream.Stream;

/**
 * How the extension joins the pieces it assembles into a tab name — the parts of a
 * body-derived name ({@code user}+{@code bob}), the numeric suffix added to a duplicate
 * ({@code api-users}+{@code 2}), the multiple selections staged for one name, and the
 * character illegal input is replaced with.
 *
 * <p>Deliberately a small closed set rather than free text: the value is spliced into a
 * regex character class in {@link TabNameGenerator}, so an arbitrary string would need
 * escaping to stay safe, and a separator that is itself stripped as an illegal character
 * (say {@code "/"}) would produce nonsense names.
 *
 * <p>Note this governs separators <em>this extension inserts</em>. Characters already
 * present in the source text are left alone: with {@link #SPACE} selected, a path segment
 * {@code user-profile} still comes through as {@code user-profile}, not {@code user profile}.
 */
public enum NameSeparator {

    HYPHEN("Hyphen — api-users", "-", ""),
    SPACE("Space — api users", " ", " "),
    UNDERSCORE("Underscore — api_users", "_", "");

    private final String label;
    private final String value;
    private final String extraAllowedChars;

    NameSeparator(String label, String value, String extraAllowedChars) {
        this.label = label;
        this.value = value;
        this.extraAllowedChars = extraAllowedChars;
    }

    /** The human-readable option shown in Burp's settings UI; also the persisted value. */
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

    /** Resolves a persisted label back to a constant, falling back to {@link #HYPHEN}. */
    public static NameSeparator fromLabel(String label) {
        return Stream.of(values())
                .filter(s -> s.label.equals(label))
                .findFirst()
                .orElse(HYPHEN);
    }
}
