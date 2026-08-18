package burp.tabrenamer;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class TabNameGeneratorTest {

    private final TabNameGenerator generator = new TabNameGenerator();

    @Test
    void getUsesLastPathSegment() {
        assertEquals("path", generator.generate("GET", "/this/is/a/path", "NONE", "", "example.com"));
    }

    @Test
    void getOnRootFallsBackToHost() {
        assertEquals("example.com", generator.generate("GET", "/", "NONE", "", "example.com"));
    }

    @Test
    void getOnEmptyPathFallsBackToHost() {
        assertEquals("example.com", generator.generate("GET", "", "NONE", "", "example.com"));
    }

    @Test
    void getOnRootWithNoHostFallsBackToRequest() {
        assertEquals("request", generator.generate("GET", "/", "NONE", "", null));
    }

    @Test
    void getOnRootWithBlankHostFallsBackToRequest() {
        assertEquals("request", generator.generate("GET", "/", "NONE", "", "  "));
    }

    @Test
    void postToRootWithNoUsableBodyFieldFallsBackToHost() {
        assertEquals("example.com", generator.generate("POST", "/", "NONE", "", "example.com"));
    }

    @Test
    void hostFallbackIsSanitized() {
        assertEquals("my-host.example.com", generator.generate("GET", "/", "NONE", "", "my host.example.com"));
    }

    @Test
    void getDecodesUrlEncodedSegment() {
        assertEquals("a-b", generator.generate("GET", "/foo/a%20b", "NONE", "", "example.com"));
    }

    @Test
    void postWithDistinctivePathUsesPath() {
        assertEquals("login", generator.generate("POST", "/api/users/login", "JSON", "{\"username\":\"bob\"}", "example.com"));
    }

    @Test
    void postWithTrailingSlashUsesLastRealSegment() {
        assertEquals("login", generator.generate("POST", "/api/users/login/", "JSON", "{\"username\":\"bob\"}", "example.com"));
    }

    @Test
    void postToGenericEndpointFallsBackToJsonPriorityKey() {
        assertEquals("bob", generator.generate("POST", "/api", "JSON", "{\"password\":\"x\",\"username\":\"bob\"}", "example.com"));
    }

    @Test
    void graphqlUsesOperationName() {
        String body = "{\"operationName\":\"GetUser\",\"variables\":{}}";
        assertEquals("GetUser", generator.generate("POST", "/graphql", "JSON", body, "example.com"));
    }

    @Test
    void postToGenericEndpointFallsBackToFirstJsonFieldWhenNoPriorityKey() {
        assertEquals("foo-bar", generator.generate("POST", "/api", "JSON", "{\"foo\":\"bar\",\"baz\":\"qux\"}", "example.com"));
    }

    @Test
    void formEncodedBodyOnGenericPathUsesFirstPair() {
        assertEquals("user-bob", generator.generate("POST", "/", "URL_ENCODED", "user=bob&pass=secret", "example.com"));
    }

    @Test
    void putIsTreatedLikePost() {
        assertEquals("id-42", generator.generate("PUT", "/api", "JSON", "{\"id\":42}", "example.com"));
    }

    @Test
    void deleteWithNoBodyUsesPath() {
        assertEquals("users", generator.generate("DELETE", "/api/users", "NONE", "", "example.com"));
    }

    @Test
    void postWithEmptyBodyFallsBackToPath() {
        assertEquals("api", generator.generate("POST", "/api", "NONE", "", "example.com"));
    }

    @Test
    void nameIsSanitizedAndTruncated() {
        String longSegment = "a".repeat(60);
        String result = generator.generate("GET", "/" + longSegment, "NONE", "", "example.com");
        assertEquals(40, result.length());
    }

    @Test
    void nameStripsIllegalCharacters() {
        assertEquals("a-b-c", generator.generate("GET", "/a b?c", "NONE", "", "example.com"));
    }

    @Test
    void sanitizeForTabNameHandlesArbitrarySelectedText() {
        assertEquals("session_id-abc123", generator.sanitizeForTabName("session_id: abc123"));
    }

    @Test
    void sanitizeForTabNameHandlesNull() {
        assertEquals("request", generator.sanitizeForTabName(null));
    }

    @Test
    void sanitizeForTabNameTruncatesLongSelections() {
        String longSelection = "x".repeat(100);
        assertEquals(40, generator.sanitizeForTabName(longSelection).length());
    }
}
