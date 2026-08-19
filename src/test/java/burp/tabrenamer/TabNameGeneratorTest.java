package burp.tabrenamer;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TabNameGeneratorTest {

    // These cover the "path, body field, or host" hierarchy, which is one of three formats now.
    private final TabNameGenerator generator = smartGenerator(NameSeparator.HYPHEN);

    private static TabNameGenerator smartGenerator(NameSeparator separator) {
        return new TabNameGenerator(() -> new NamingConfig(separator, NamingFormat.SMART));
    }

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
        assertEquals("a-b-c", generator.generate("GET", "/a b*c", "NONE", "", "example.com"));
    }

    @Test
    void anyQueryStringThatSneaksIntoThePathIsDropped() {
        // Montoya passes a path with the query already removed; this keeps every format
        // behaving the same if a caller ever passes a raw target instead.
        assertEquals("postMessage",
                generator.generate("GET", "/apps/postMessage?mailboxid=123", "NONE", "", "example.com"));
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
        return smartGenerator(separator);
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
                .generate("GET", "/a b*c", "NONE", "", "example.com"));
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
        TabNameGenerator live = new TabNameGenerator(() -> new NamingConfig(current[0], NamingFormat.SMART));
        assertEquals("user-bob", live.generate("POST", "/", "URL_ENCODED", "user=bob", "example.com"));
        current[0] = NameSeparator.SPACE;
        assertEquals("user bob", live.generate("POST", "/", "URL_ENCODED", "user=bob", "example.com"));
    }

    @Test
    void nullConfigSupplierValueFallsBackToTheDefaults() {
        // Defaults are method-and-path, so a null config must still produce a usable name.
        assertEquals("POST | /api", new TabNameGenerator(() -> null)
                .generate("POST", "/api", "URL_ENCODED", "user=bob", "example.com"));
    }

    // --- duplicate names --------------------------------------------------------------------

    @Test
    void uniqueNameIsUnchangedWhenNothingElseUsesIt() {
        assertEquals("api-users", generator.uniqueAmong("api-users", List.of("login", "graphql")));
        assertEquals("api-users", generator.uniqueAmong("api-users", List.of()));
    }

    @Test
    void duplicateNamesGetBracketedNumbersFromTwo() {
        assertEquals("admin (2)", generator.uniqueAmong("admin", List.of("admin")));
        assertEquals("admin (3)", generator.uniqueAmong("admin", List.of("admin", "admin (2)")));
    }

    @Test
    void duplicateNumberingFillsGapsLeftByClosedTabs() {
        // "admin (2)" was closed: its number is free again, so it is reused rather than skipped.
        assertEquals("admin (2)", generator.uniqueAmong("admin", List.of("admin", "admin (3)")));
    }

    @Test
    void duplicateNumberingNeverCollidesWithAManuallyTypedName() {
        assertEquals("admin (3)", generator.uniqueAmong("admin", List.of("admin", "admin (2)")));
    }

    @Test
    void duplicateSuffixIsIndependentOfTheConfiguredSeparator() {
        assertEquals("POST users (2)", generatorWith(NameSeparator.SPACE)
                .uniqueAmong("POST users", List.of("POST users")));
        assertEquals("POST_users (2)", generatorWith(NameSeparator.UNDERSCORE)
                .uniqueAmong("POST_users", List.of("POST_users")));
        assertEquals("POST-users (2)", generatorWith(NameSeparator.HYPHEN)
                .uniqueAmong("POST-users", List.of("POST-users")));
    }

    @Test
    void duplicateSuffixKeepsNameWithinTheLengthCap() {
        String base = "a".repeat(40);
        String result = generator.uniqueAmong(base, List.of(base));
        assertEquals(40, result.length());
        assertEquals("a".repeat(36) + " (2)", result);
    }

    @Test
    void twoDigitDuplicateSuffixAlsoStaysWithinTheLengthCap() {
        String base = "a".repeat(40);
        List<String> taken = new ArrayList<>(List.of(base));
        for (int n = 2; n <= 10; n++) {
            taken.add(generator.withOrdinalSuffix(base, n));
        }
        String result = generator.uniqueAmong(base, taken);
        assertEquals("a".repeat(35) + " (11)", result);
        assertEquals(40, result.length());
    }

    @Test
    void duplicateSuffixDoesNotLeaveADanglingSeparatorOrSpace() {
        String hyphenated = "a".repeat(35) + "-bbbb";
        assertEquals("a".repeat(35) + " (2)", generator.uniqueAmong(hyphenated, List.of(hyphenated)));

        TabNameGenerator spaced = generatorWith(NameSeparator.SPACE);
        String spacedName = "a".repeat(35) + " bbbb";
        assertEquals("a".repeat(35) + " (2)", spaced.uniqueAmong(spacedName, List.of(spacedName)));
    }

    @Test
    void existingTitlesAreComparedIgnoringSurroundingWhitespace() {
        assertEquals("admin (2)", generator.uniqueAmong("admin", List.of("  admin  ")));
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

    // --- large / hostile bodies -------------------------------------------------------------

    @Test
    void bodyIsScannedOnlyUpToTheCap() {
        // "username" is a priority key and would win outright, but it sits past the scan cap,
        // so the first field inside the cap is used instead of the whole body being walked.
        String padding = "\"pad\":\"x\",".repeat(1000);
        String body = "{\"foo\":\"bar\"," + padding + "\"username\":\"bob\"}";
        assertEquals("foo-bar", generator.generate("POST", "/api", "JSON", body, "example.com"));
    }

    @Test
    void aPriorityKeyInsideTheCapIsStillPreferred() {
        String body = "{\"foo\":\"bar\",\"username\":\"bob\"}";
        assertEquals("bob", generator.generate("POST", "/api", "JSON", body, "example.com"));
    }

    @Test
    void hugeBodyDoesNotStallNameGeneration() {
        String body = "{\"padding\":\"" + "p".repeat(2_000_000) + "\"}";
        long start = System.nanoTime();
        assertEquals("api", generator.generate("POST", "/api", "JSON", body, "example.com"));
        assertTrue(System.nanoTime() - start < 1_000_000_000L, "naming a 2MB body should not take a second");
    }

    @Test
    void bodyOfOnlyControlCharactersFallsBackRatherThanProducingAnEmptyName() {
        assertEquals("example.com", generator.generate("POST", "/", "JSON", "\u0000\u0001\u0002", "example.com"));
    }

    @Test
    void controlCharactersInSelectedTextAreSanitized() {
        // Replaced by the separator and collapsed, like any other run of illegal characters.
        assertEquals("a-b", generator.sanitizeForTabName("a\u0000\u0001b"));
        assertEquals("request", generator.sanitizeForTabName("\u0000\u0001\u0002"));
    }
}
