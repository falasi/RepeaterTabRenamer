package burp.tabrenamer;

import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

/**
 * What an automatically generated tab name is built from when a request is sent.
 *
 * <p>Only affects automatic naming. Names you build yourself — from a selection or from staged
 * parts — are always exactly what you selected, whichever format is chosen.
 */
public enum NamingFormat {

    /**
     * {@code POST | /apps/emailShare/postMessage} — method and full path, no query string.
     *
     * <p>The default, because it's the format that stays useful as a session grows: the method
     * distinguishes a GET from the POST to the same endpoint, and the full path distinguishes
     * endpoints that happen to end in the same word ({@code /users/create} vs
     * {@code /admin/create}), which is exactly where {@link #LAST_PATH_SEGMENT} starts producing
     * tabs you can't tell apart.
     */
    METHOD_AND_PATH("Method and path"),

    /** {@code postMessage} — just the last path segment, falling back to the host. */
    LAST_PATH_SEGMENT("Last path segment"),

    /**
     * The original behaviour: last path segment, but when the path is too generic to identify a
     * request ({@code /api}, {@code /graphql}) a likely-identifying field from the body is used
     * instead, falling back to the host. Best for APIs where one endpoint serves many
     * operations.
     */
    SMART("Path, body field, or host");

    private final String label;

    NamingFormat(String label) {
        this.label = label;
    }

    /** The option shown in Burp's settings UI; also the value Burp persists. */
    public String label() {
        return label;
    }

    public static List<String> labels() {
        return Stream.of(values()).map(NamingFormat::label).toList();
    }

    /** @see SettingOption#resolve */
    public static Optional<NamingFormat> fromSetting(String value) {
        return SettingOption.resolve(values(), NamingFormat::label, value);
    }
}
