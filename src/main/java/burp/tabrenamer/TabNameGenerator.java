package burp.tabrenamer;

import java.util.List;
import java.util.Set;
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
 */
public final class TabNameGenerator {

    private static final int MAX_LENGTH = 40;

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

    /**
     * @param contentTypeName Burp's parsed content-type classification for the body
     *                        (e.g. "JSON", "URL_ENCODED", "NONE", ...), as produced by
     *                        Montoya's {@code HttpRequest.contentType().name()}. May be null.
     * @param host            the request's target host, used as a fallback name when the path
     *                        has no usable segment (root "/" or empty) and no body field could
     *                        be extracted either. May be null.
     */
    public String generate(String method, String pathWithoutQuery, String contentTypeName, String body, String host) {
        String pathName = lastPathSegment(pathWithoutQuery);
        boolean bodyBearing = method != null && !method.equalsIgnoreCase("GET") && !method.equalsIgnoreCase("HEAD");
        boolean hasBody = body != null && !body.isBlank();

        String name;
        if (bodyBearing && hasBody && isGeneric(pathName)) {
            String fromBody = extractFromBody(contentTypeName, body);
            name = (fromBody != null) ? fromBody : fallbackName(pathName, host);
        } else {
            name = fallbackName(pathName, host);
        }

        return sanitize(name);
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

    private String extractFromBody(String contentTypeName, String body) {
        String type = contentTypeName == null ? "" : contentTypeName.toUpperCase();

        if (type.equals("URL_ENCODED") || (type.isEmpty() && looksFormEncoded(body))) {
            String fromForm = firstFormPair(body);
            if (fromForm != null) {
                return fromForm;
            }
        }

        // Try JSON-style extraction for JSON bodies, and as a fallback for anything
        // unrecognized: many APIs (and GraphQL) send JSON without an accurate
        // Content-Type header, so Burp may classify it as UNKNOWN.
        for (String key : PRIORITY_BODY_KEYS) {
            String value = findJsonField(body, key);
            if (value != null && !value.isBlank()) {
                return value;
            }
        }

        String firstField = firstJsonField(body);
        if (firstField != null) {
            return firstField;
        }

        return null;
    }

    private boolean looksFormEncoded(String body) {
        String trimmed = body.trim();
        return !trimmed.startsWith("{") && !trimmed.startsWith("[") && trimmed.contains("=");
    }

    private String firstFormPair(String body) {
        String firstPair = body.trim().split("&", 2)[0];
        if (firstPair.isEmpty()) {
            return null;
        }
        String[] kv = firstPair.split("=", 2);
        String key = urlDecode(kv[0]);
        String value = kv.length > 1 ? urlDecode(kv[1]) : "";
        return value.isEmpty() ? key : key + "-" + value;
    }

    private String findJsonField(String body, String key) {
        Matcher m = JSON_STRING_FIELD.matcher(body);
        while (m.find()) {
            if (m.group(1).equalsIgnoreCase(key)) {
                return m.group(2);
            }
        }
        m = JSON_SCALAR_FIELD.matcher(body);
        while (m.find()) {
            if (m.group(1).equalsIgnoreCase(key)) {
                return key + "-" + m.group(2);
            }
        }
        return null;
    }

    private String firstJsonField(String body) {
        Matcher m = JSON_STRING_FIELD.matcher(body);
        if (m.find()) {
            return m.group(1) + "-" + m.group(2);
        }
        m = JSON_SCALAR_FIELD.matcher(body);
        if (m.find()) {
            return m.group(1) + "-" + m.group(2);
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
        return raw == null ? "request" : sanitize(raw);
    }

    private String sanitize(String raw) {
        String cleaned = raw.trim()
                .replaceAll("[^A-Za-z0-9._-]+", "-")
                .replaceAll("-{2,}", "-");
        while (cleaned.startsWith("-")) {
            cleaned = cleaned.substring(1);
        }
        while (cleaned.endsWith("-")) {
            cleaned = cleaned.substring(0, cleaned.length() - 1);
        }
        if (cleaned.isEmpty()) {
            cleaned = "request";
        }
        if (cleaned.length() > MAX_LENGTH) {
            cleaned = cleaned.substring(0, MAX_LENGTH);
        }
        return cleaned;
    }
}
