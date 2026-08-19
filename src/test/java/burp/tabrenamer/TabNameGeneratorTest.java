package burp.tabrenamer;

import org.junit.jupiter.api.Test;

import java.util.List;

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

    // --- separator preference ---------------------------------------------------------------

    private TabNameGenerator generatorWith(NameSeparator separator) {
        return new TabNameGenerator(() -> separator);
    }

    @Test
    void spaceSeparatorJoinsBodyDerivedParts() {
        assertEquals("user bob", generatorWith(NameSeparator.SPACE)
                .generate("POST", "/", "URL_ENCODED", "user=bob&pass=secret", "example.com"));
    }

    @Test
    void underscoreSeparatorJoinsBodyDerivedParts() {
        assertEquals("user_bob", generatorWith(NameSeparator.UNDERSCORE)
                .generate("POST", "/", "URL_ENCODED", "user=bob&pass=secret", "example.com"));
    }

    @Test
    void spaceSeparatorReplacesIllegalCharactersWithSpaces() {
        assertEquals("a b c", generatorWith(NameSeparator.SPACE)
                .generate("GET", "/a b?c", "NONE", "", "example.com"));
    }

    @Test
    void spaceSeparatorCollapsesRunsAndTrimsEnds() {
        assertEquals("user profile", generatorWith(NameSeparator.SPACE)
                .sanitizeForTabName("  user???profile!  "));
    }

    @Test
    void separatorDoesNotRewriteCharactersAlreadyInTheSource() {
        // The preference governs separators the extension inserts, not ones already in the text.
        assertEquals("user-profile", generatorWith(NameSeparator.SPACE)
                .generate("GET", "/user-profile", "NONE", "", "example.com"));
    }

    @Test
    void separatorAppliesToManuallySelectedText() {
        assertEquals("session_id abc123", generatorWith(NameSeparator.SPACE)
                .sanitizeForTabName("session_id: abc123"));
        assertEquals("session_id_abc123", generatorWith(NameSeparator.UNDERSCORE)
                .sanitizeForTabName("session_id: abc123"));
    }

    @Test
    void separatorIsReadPerCallSoSettingChangesApplyImmediately() {
        NameSeparator[] current = {NameSeparator.HYPHEN};
        TabNameGenerator live = new TabNameGenerator(() -> current[0]);
        assertEquals("user-bob", live.generate("POST", "/", "URL_ENCODED", "user=bob", "example.com"));
        current[0] = NameSeparator.SPACE;
        assertEquals("user bob", live.generate("POST", "/", "URL_ENCODED", "user=bob", "example.com"));
    }

    @Test
    void nullSeparatorSupplierValueFallsBackToHyphen() {
        assertEquals("user-bob", new TabNameGenerator(() -> null)
                .generate("POST", "/", "URL_ENCODED", "user=bob", "example.com"));
    }

    @Test
    void unknownPersistedSeparatorLabelFallsBackToHyphen() {
        assertEquals(NameSeparator.HYPHEN, NameSeparator.fromLabel("something else"));
        assertEquals(NameSeparator.HYPHEN, NameSeparator.fromLabel(null));
    }

    // --- duplicate names --------------------------------------------------------------------

    @Test
    void uniqueNameIsUnchangedWhenNothingElseUsesIt() {
        assertEquals("api-users", generator.uniqueAmong("api-users", List.of("login", "graphql")));
        assertEquals("api-users", generator.uniqueAmong("api-users", List.of()));
    }

    @Test
    void duplicateNamesGetNumberedFromTwo() {
        assertEquals("api-users-2", generator.uniqueAmong("api-users", List.of("api-users")));
        assertEquals("api-users-3", generator.uniqueAmong("api-users", List.of("api-users", "api-users-2")));
    }

    @Test
    void duplicateNumberingFillsGapsLeftByClosedTabs() {
        // "api-users-2" was closed: its number is free again, so it is reused rather than skipped.
        assertEquals("api-users-2", generator.uniqueAmong("api-users", List.of("api-users", "api-users-3")));
    }

    @Test
    void duplicateNumberingNeverCollidesWithAManuallyTypedName() {
        assertEquals("api-users-3", generator.uniqueAmong("api-users", List.of("api-users", "api-users-2")));
    }

    @Test
    void duplicateSuffixUsesTheConfiguredSeparator() {
        TabNameGenerator spaced = generatorWith(NameSeparator.SPACE);
        assertEquals("api users 2", spaced.uniqueAmong("api users", List.of("api users")));
    }

    @Test
    void duplicateSuffixKeepsNameWithinTheLengthCap() {
        String base = "a".repeat(40);
        String result = generator.uniqueAmong(base, List.of(base));
        assertEquals(40, result.length());
        assertEquals("a".repeat(38) + "-2", result);
    }

    @Test
    void duplicateSuffixDoesNotLeaveADanglingSeparator() {
        String base = "a".repeat(37) + "-bb";
        assertEquals("a".repeat(37) + "-2", generator.uniqueAmong(base, List.of(base)));
    }

    @Test
    void existingTitlesAreComparedIgnoringSurroundingWhitespace() {
        assertEquals("api-users-2", generator.uniqueAmong("api-users", List.of("  api-users  ")));
    }

    // --- multi-part names -------------------------------------------------------------------

    @Test
    void partsAreJoinedWithTheConfiguredSeparator() {
        List<String> parts = List.of("POST", "users", "admin");
        assertEquals("POST-users-admin", generator.joinParts(parts));
        assertEquals("POST users admin", generatorWith(NameSeparator.SPACE).joinParts(parts));
        assertEquals("POST_users_admin", generatorWith(NameSeparator.UNDERSCORE).joinParts(parts));
    }

    @Test
    void partsAreIndividuallySanitized() {
        assertEquals("POST-api-v2-users", generator.joinParts(List.of(" POST ", "/api/v2/", "users")));
    }

    @Test
    void partsThatSanitizeAwayEntirelyAreDroppedRatherThanLeavingGaps() {
        assertEquals("POST-users", generator.joinParts(List.of("POST", "  ", "!!!", "users")));
    }

    @Test
    void joinedPartsAreLengthCapped() {
        assertEquals(40, generator.joinParts(List.of("x".repeat(30), "y".repeat(30))).length());
    }

    @Test
    void joiningNoPartsFallsBackToRequest() {
        assertEquals("request", generator.joinParts(List.of()));
        assertEquals("request", generator.joinParts(null));
        assertEquals("request", generator.joinParts(List.of("!!!")));
    }
}
