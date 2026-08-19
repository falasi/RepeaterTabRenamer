package burp.tabrenamer;

/**
 * The naming preferences as one value, so every route into a name reads the same snapshot.
 *
 * <p>This is the single hop between settings and naming: {@link NamingSettings} produces it,
 * {@link TabNameGenerator} consumes it, and nothing else interprets a preference. Handlers that
 * formatted names themselves are what let the separator drift out of sync before.
 */
public record NamingConfig(NameSeparator separator, NamingFormat format) {

    /** Used before any preference is read, and by tests that don't care which is in force. */
    public static final NamingConfig DEFAULT =
            new NamingConfig(NameSeparator.HYPHEN, NamingFormat.METHOD_AND_PATH);

    // Referencing the constants rather than DEFAULT: DEFAULT is itself built by this
    // constructor, so it is still null while the class initialises.
    public NamingConfig {
        separator = separator == null ? NameSeparator.HYPHEN : separator;
        format = format == null ? NamingFormat.METHOD_AND_PATH : format;
    }
}
