package burp.tabrenamer;

import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.function.Supplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Turns a request (method, path, content-type, body) into a short, readable
 * Repeater tab name.
 *
 * Strategy: use the last URL path segment when it's distinctive. Only fall
 * back to inspecting the body when the path itself doesn't tell requests
 * apart (e.g. a single "/graphql" or "/api" endpoint handling many
 * different operations) — otherwise every POST tab would end up decorated
 * with body noise even when the path already says everything ("/login").
 *
 * <p>This is the single place that knows how name pieces are joined and what a legal tab
 * name looks like, so the user's {@link NameSeparator} preference is applied consistently to
 * every route into a name: automatic naming, manually selected text, multi-part names, and
 * the numeric suffix that distinguishes duplicates.
 */
public final class TabNameGenerator {

    private static final int MAX_LENGTH = 40;

    /** Upper bound on duplicate suffixes; past this, a collision just keeps the base name. */
    private static final int MAX_DUPLICATE_SUFFIX = 99;

    /**
     * How many characters of a request body are scanned for a name-worthy field. A body can be
     * megabytes (file uploads, bulk imports) and this runs on Burp's HTTP thread for every
     * Repeater send, so the scan is bounded: an identifying field that isn't in the first few KB
     * wasn't going to make a good tab name anyway.
     */
    private static final int MAX_BODY_SCAN = 8192;

    /** Path segments too generic to identify a request on their own. */
    private static final Set<String> GENERIC_SEGMENTS = Set.of(
            "", "api", "graphql", "gql", "rpc", "query", "index", "index.php",
            "service", "services", "endpoint", "v1", "v2", "v3", "json", "jsonrpc"
    );

    /** JSON keys checked in priority order when a request body must be sniffed. */
    private static final List<String> PRIORITY_BODY_KEYS = List.of(
            "operationName", "action", "method", "type", "event",
            "name", "username", "email", "id"
    );

    private static final Pattern JSON_STRING_FIELD =
            Pattern.compile("\"([A-Za-z0-9_]+)\"\\s*:\\s*\"([^\"]{1,60})\"");
    private static final Pattern JSON_SCALAR_FIELD =
            Pattern.compile("\"([A-Za-z0-9_]+)\"\\s*:\\s*(-?[0-9]+(?:\\.[0-9]+)?|true|false)");

    private final Supplier<NameSeparator> separatorSource;

    /** Uses the built-in default separator; handy for tests and for any caller with no settings. */
    public TabNameGenerator() {
        this(() -> NameSeparator.HYPHEN);
    }

    /**
     * @param separatorSource read on every call rather than once at construction, so a change
     *                        in Burp's settings panel applies immediately.
     */
    public TabNameGenerator(Supplier<NameSeparator> separatorSource) {
        this.separatorSource = separatorSource;
    }

    /**
     * @param contentTypeName Burp's parsed content-type classification for the body
     *                        (e.g. "JSON", "URL_ENCODED", "NONE", ...), as produced by
     *                        Montoya's {@code HttpRequest.contentType().name()}. May be null.
     * @param host            the request's target host, used as a fallback name when the path
     *                        has no usable segment (root "/" or empty) and no body field could
     *                        be extracted either. May be null.
     */
    public String generate(String method, String pathWithoutQuery, String contentTypeName, String body, String host) {
        NameSeparator separator = separator();
        String pathName = lastPathSegment(pathWithoutQuery);
        boolean bodyBearing = method != null && !method.equalsIgnoreCase("GET") && !method.equalsIgnoreCase("HEAD");
        boolean hasBody = body != null && !body.isBlank();

        String name;
        if (bodyBearing && hasBody && isGeneric(pathName)) {
            String fromBody = extractFromBody(contentTypeName, body, separator);
            name = (fromBody != null) ? fromBody : fallbackName(pathName, host);
        } else {
            name = fallbackName(pathName, host);
        }

        return sanitize(name, separator);
    }

    /** Used whenever there's no usable path segment: the host is a far more useful tab name
     *  than a literal "request" repeated across every tab hitting a different site's "/". */
    private String fallbackName(String pathName, String host) {
        if (!pathName.isEmpty()) {
            return pathName;
        }
        if (host != null && !host.isBlank()) {
            return host;
        }
        return "request";
    }

    private boolean isGeneric(String pathName) {
        return GENERIC_SEGMENTS.contains(pathName.toLowerCase());
    }

    private String lastPathSegment(String pathWithoutQuery) {
        if (pathWithoutQuery == null || pathWithoutQuery.isEmpty()) {
            return "";
        }
        String path = pathWithoutQuery;
        int hashIdx = path.indexOf('#');
        if (hashIdx >= 0) {
            path = path.substring(0, hashIdx);
        }
        while (path.endsWith("/")) {
            path = path.substring(0, path.length() - 1);
        }
        int lastSlash = path.lastIndexOf('/');
        String segment = lastSlash >= 0 ? path.substring(lastSlash + 1) : path;
        return urlDecode(segment);
    }

    private String extractFromBody(String contentTypeName, String fullBody, NameSeparator separator) {
        String type = contentTypeName == null ? "" : contentTypeName.toUpperCase();
        String body = fullBody.length() > MAX_BODY_SCAN ? fullBody.substring(0, MAX_BODY_SCAN) : fullBody;

        if (type.equals("URL_ENCODED") || (type.isEmpty() && looksFormEncoded(body))) {
            String fromForm = firstFormPair(body, separator);
            if (fromForm != null) {
                return fromForm;
            }
        }

        // Try JSON-style extraction for JSON bodies, and as a fallback for anything
        // unrecognized: many APIs (and GraphQL) send JSON without an accurate
        // Content-Type header, so Burp may classify it as UNKNOWN.
        for (String key : PRIORITY_BODY_KEYS) {
            String value = findJsonField(body, key, separator);
            if (value != null && !value.isBlank()) {
                return value;
            }
        }

        String firstField = firstJsonField(body, separator);
        if (firstField != null) {
            return firstField;
        }

        return null;
    }

    private boolean looksFormEncoded(String body) {
        String trimmed = body.trim();
        return !trimmed.startsWith("{") && !trimmed.startsWith("[") && trimmed.contains("=");
    }

    private String firstFormPair(String body, NameSeparator separator) {
        String firstPair = body.trim().split("&", 2)[0];
        if (firstPair.isEmpty()) {
            return null;
        }
        String[] kv = firstPair.split("=", 2);
        String key = urlDecode(kv[0]);
        String value = kv.length > 1 ? urlDecode(kv[1]) : "";
        return value.isEmpty() ? key : key + separator.value() + value;
    }

    private String findJsonField(String body, String key, NameSeparator separator) {
        Matcher m = JSON_STRING_FIELD.matcher(body);
        while (m.find()) {
            if (m.group(1).equalsIgnoreCase(key)) {
                return m.group(2);
            }
        }
        m = JSON_SCALAR_FIELD.matcher(body);
        while (m.find()) {
            if (m.group(1).equalsIgnoreCase(key)) {
                return key + separator.value() + m.group(2);
            }
        }
        return null;
    }

    private String firstJsonField(String body, NameSeparator separator) {
        Matcher m = JSON_STRING_FIELD.matcher(body);
        if (m.find()) {
            return m.group(1) + separator.value() + m.group(2);
        }
        m = JSON_SCALAR_FIELD.matcher(body);
        if (m.find()) {
            return m.group(1) + separator.value() + m.group(2);
        }
        return null;
    }

    private String urlDecode(String value) {
        try {
            return java.net.URLDecoder.decode(value, java.nio.charset.StandardCharsets.UTF_8);
        } catch (Exception e) {
            return value;
        }
    }

    /**
     * Sanitizes arbitrary user-selected text (from a request/response editor) into a tab
     * name, using the same rules {@link #generate} applies to generated names.
     */
    public String sanitizeForTabName(String raw) {
        return raw == null ? "request" : sanitize(raw, separator());
    }

    /**
     * Joins several separately captured selections into one name, e.g. {@code POST} +
     * {@code users} + {@code admin} → {@code POST-users-admin}. Each part is cleaned on its own
     * first, so a part that sanitizes away to nothing is dropped rather than leaving a dangling
     * separator, and only the joined result is length-capped.
     */
    public String joinParts(List<String> parts) {
        if (parts == null || parts.isEmpty()) {
            return "request";
        }
        NameSeparator separator = separator();
        String joined = parts.stream()
                .filter(Objects::nonNull)
                .map(part -> normalize(part, separator))
                .filter(part -> !part.isEmpty())
                .reduce((a, b) -> a + separator.value() + b)
                .orElse("");
        return truncate(joined.isEmpty() ? "request" : joined, separator);
    }

    /**
     * Returns {@code base} if no tab already carries it, otherwise the first free
     * {@code base (n)}, starting at 2 ({@code api-users}, {@code api-users (2)},
     * {@code api-users (3)}).
     *
     * <p>Uniqueness is decided against the tab titles that exist right now, not against a
     * tally the extension keeps: closing {@code api-users} frees the plain name again for the
     * next request, and a name the user typed by hand is automatically avoided because it's
     * one of the titles being compared against.
     */
    public String uniqueAmong(String base, Collection<String> taken) {
        if (taken == null || taken.isEmpty()) {
            return base;
        }
        Set<String> takenTitles = new HashSet<>();
        for (String title : taken) {
            if (title != null) {
                takenTitles.add(title.trim());
            }
        }
        if (!takenTitles.contains(base)) {
            return base;
        }
        for (int n = 2; n <= MAX_DUPLICATE_SUFFIX; n++) {
            String candidate = withOrdinalSuffix(base, n);
            if (!takenTitles.contains(candidate)) {
                return candidate;
            }
        }
        return base;
    }

    /**
     * Appends " (n)", trimming the base first if the suffix wouldn't otherwise fit inside the
     * length cap.
     *
     * <p>Deliberately fixed rather than following the {@link NameSeparator} preference: the
     * number isn't part of the name, it's a disambiguator, and a bracketed suffix stays visibly
     * distinct from the name's own words whichever separator is in use — "POST_users_2" reads
     * like a third name part, "POST_users (2)" doesn't.
     */
    String withOrdinalSuffix(String base, int n) {
        String suffix = " (" + n + ")";
        String trimmedBase = base;
        if (trimmedBase.length() + suffix.length() > MAX_LENGTH) {
            trimmedBase = trimmedBase.substring(0, Math.max(0, MAX_LENGTH - suffix.length()));
        }
        trimmedBase = trimNameEnd(trimmedBase, separator());
        return trimmedBase.isEmpty() ? "request" + suffix : trimmedBase + suffix;
    }

    private NameSeparator separator() {
        NameSeparator separator = separatorSource.get();
        return separator == null ? NameSeparator.HYPHEN : separator;
    }

    private String sanitize(String raw, NameSeparator separator) {
        String cleaned = normalize(raw, separator);
        return truncate(cleaned.isEmpty() ? "request" : cleaned, separator);
    }

    /**
     * Replaces anything that isn't legal in a tab name with the configured separator, collapses
     * runs of it, and trims it from both ends. Length is deliberately left to
     * {@link #truncate} so multi-part names can be cleaned piece by piece and capped once.
     */
    private String normalize(String raw, NameSeparator separator) {
        // The '-' stays last inside the character class so it's read as a literal rather than
        // as the start of a range once SPACE contributes an extra allowed character.
        String illegal = "[^A-Za-z0-9._" + separator.extraAllowedChars() + "-]+";
        String cleaned = raw.trim()
                .replaceAll(illegal, Matcher.quoteReplacement(separator.value()))
                .replaceAll("(?:" + Pattern.quote(separator.value()) + "){2,}",
                        Matcher.quoteReplacement(separator.value()));
        return stripSeparator(cleaned, separator);
    }

    private String truncate(String name, NameSeparator separator) {
        if (name.length() <= MAX_LENGTH) {
            return name;
        }
        // Cutting mid-name can leave a trailing separator ("api-users-" ), which reads as though
        // something went missing — drop it.
        String truncated = trimNameEnd(name.substring(0, MAX_LENGTH), separator);
        return truncated.isEmpty() ? "request" : truncated;
    }

    /** Strips trailing separators and whitespace left behind by a mid-name cut, in any order. */
    private String trimNameEnd(String value, NameSeparator separator) {
        String previous;
        String result = value;
        do {
            previous = result;
            result = stripSeparator(result.strip(), separator);
        } while (!result.equals(previous));
        return result;
    }

    private String stripSeparator(String value, NameSeparator separator) {
        String sep = separator.value();
        String result = value;
        while (result.startsWith(sep)) {
            result = result.substring(sep.length());
        }
        while (result.endsWith(sep)) {
            result = result.substring(0, result.length() - sep.length());
        }
        return result;
    }
}
