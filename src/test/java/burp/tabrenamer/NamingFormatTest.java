package burp.tabrenamer;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The automatic naming formats, and the method-and-path default in particular. */
class NamingFormatTest {

    private TabNameGenerator with(NamingFormat format) {
        return with(format, NameSeparator.HYPHEN);
    }

    private TabNameGenerator with(NamingFormat format, NameSeparator separator) {
        return new TabNameGenerator(() -> new NamingConfig(separator, format));
    }

    private final TabNameGenerator methodAndPath = with(NamingFormat.METHOD_AND_PATH);

    // --- method and path --------------------------------------------------------------------

    @Test
    void isTheDefaultFormat() {
        assertEquals(NamingFormat.METHOD_AND_PATH, NamingConfig.DEFAULT.format());
        assertEquals("POST | /apps/emailShare/postMessage",
                new TabNameGenerator().generate("POST", "/apps/emailShare/postMessage", "NONE", "", "example.com"));
    }

    @Test
    void combinesMethodAndFullPath() {
        assertEquals("POST | /apps/emailShare/postMessage",
                methodAndPath.generate("POST", "/apps/emailShare/postMessage", "NONE", "", "example.com"));
        assertEquals("GET | /users", methodAndPath.generate("GET", "/users", "NONE", "", "example.com"));
    }

    @Test
    void stripsTheQueryString() {
        assertEquals("POST | /apps/emailShare/postMessage", methodAndPath.generate(
                "POST", "/apps/emailShare/postMessage?mailboxid=12345", "NONE", "", "example.com"));
    }

    @Test
    void stripsTheFragment() {
        assertEquals("GET | /docs/page",
                methodAndPath.generate("GET", "/docs/page#section", "NONE", "", "example.com"));
    }

    @Test
    void neverIncludesTheHost() {
        String name = methodAndPath.generate("GET", "/users", "NONE", "", "internal.example.com");
        assertTrue(name.contains("/users"));
        assertEquals("GET | /users", name);
    }

    @Test
    void normalisesRedundantTrailingSlashes() {
        assertEquals("GET | /apps/emailShare",
                methodAndPath.generate("GET", "/apps/emailShare/", "NONE", "", "example.com"));
        assertEquals("GET | /apps/emailShare",
                methodAndPath.generate("GET", "/apps/emailShare///", "NONE", "", "example.com"));
    }

    @Test
    void keepsTheRootPathAsASingleSlash() {
        assertEquals("GET | /", methodAndPath.generate("GET", "/", "NONE", "", "example.com"));
        assertEquals("GET | /", methodAndPath.generate("GET", "", "NONE", "", "example.com"));
        assertEquals("GET | /", methodAndPath.generate("GET", null, "NONE", "", "example.com"));
    }

    @Test
    void usesTheSameDividerWhateverTheSeparatorPreference() {
        for (NameSeparator separator : NameSeparator.values()) {
            assertEquals("POST | /apps/emailShare",
                    with(NamingFormat.METHOD_AND_PATH, separator)
                            .generate("POST", "/apps/emailShare", "NONE", "", "example.com"),
                    "method/path divider must stay \" | \" under " + separator);
        }
    }

    @Test
    void appliesTheSeparatorToIllegalCharactersInsideThePath() {
        assertEquals("GET | /apps/email-share",
                methodAndPath.generate("GET", "/apps/email share", "NONE", "", "example.com"));
        assertEquals("GET | /apps/email share",
                with(NamingFormat.METHOD_AND_PATH, NameSeparator.SPACE)
                        .generate("GET", "/apps/email share", "NONE", "", "example.com"));
    }

    @Test
    void dropsWholeLeadingSegmentsWhenThePathIsTooLong() {
        String name = methodAndPath.generate(
                "POST", "/apps/emailShare/compose/attachments/postMessage", "NONE", "", "example.com");
        assertTrue(name.length() <= 40, "was " + name.length() + ": " + name);
        assertTrue(name.startsWith("POST | …/"), name);
        assertTrue(name.endsWith("/postMessage"), "the endpoint must survive truncation: " + name);
    }

    @Test
    void keepsTheTailOfASingleOverlongSegment() {
        String name = methodAndPath.generate("GET", "/" + "a".repeat(80) + "endpoint", "NONE", "", "example.com");
        assertTrue(name.length() <= 40, "was " + name.length());
        assertTrue(name.startsWith("GET | …"), name);
        assertTrue(name.endsWith("endpoint"), "the distinguishing tail must survive: " + name);
    }

    @Test
    void staysWithinTheLengthCapForAnAbsurdMethod() {
        String name = methodAndPath.generate("M".repeat(60), "/some/path", "NONE", "", "example.com");
        assertTrue(name.length() <= 40, "was " + name.length() + ": " + name);
    }

    @Test
    void fallsBackToThePathAloneWhenThereIsNoMethod() {
        assertEquals("/users", methodAndPath.generate(null, "/users", "NONE", "", "example.com"));
        assertEquals("/users", methodAndPath.generate("  ", "/users", "NONE", "", "example.com"));
    }

    @Test
    void duplicateNumberingWorksOnAMethodAndPathName() {
        String base = "POST | /apps/emailShare/postMessage";
        assertEquals("POST | /apps/emailShare/postMessage (2)", methodAndPath.uniqueAmong(base, List.of(base)));
        assertEquals("POST | /apps/emailShare/postMessage (3)",
                methodAndPath.uniqueAmong(base, List.of(base, "POST | /apps/emailShare/postMessage (2)")));
    }

    @Test
    void duplicateNumberingStaysWithinTheCapOnALongMethodAndPathName() {
        String base = methodAndPath.generate(
                "DELETE", "/apps/emailShare/compose/attachments/item", "NONE", "", "example.com");
        String numbered = methodAndPath.uniqueAmong(base, List.of(base));
        assertTrue(numbered.length() <= 40, "was " + numbered.length() + ": " + numbered);
        assertTrue(numbered.endsWith(" (2)"), numbered);
    }

    // --- last path segment ------------------------------------------------------------------

    @Test
    void lastPathSegmentUsesOnlyTheFinalSegment() {
        TabNameGenerator generator = with(NamingFormat.LAST_PATH_SEGMENT);
        assertEquals("postMessage",
                generator.generate("POST", "/apps/emailShare/postMessage", "NONE", "", "example.com"));
        assertEquals("postMessage",
                generator.generate("POST", "/apps/emailShare/postMessage/", "NONE", "", "example.com"));
    }

    @Test
    void lastPathSegmentIgnoresTheBodyEvenOnAGenericPath() {
        // This is what separates it from SMART: no body sniffing, so /api stays /api.
        assertEquals("api", with(NamingFormat.LAST_PATH_SEGMENT)
                .generate("POST", "/api", "JSON", "{\"operationName\":\"GetUser\"}", "example.com"));
        assertEquals("GetUser", with(NamingFormat.SMART)
                .generate("POST", "/api", "JSON", "{\"operationName\":\"GetUser\"}", "example.com"));
    }

    @Test
    void lastPathSegmentFallsBackToTheHostAtTheRoot() {
        assertEquals("example.com",
                with(NamingFormat.LAST_PATH_SEGMENT).generate("GET", "/", "NONE", "", "example.com"));
    }

    // --- setting resolution -----------------------------------------------------------------

    @Test
    void everyOfferedFormatLabelResolvesBack() {
        for (NamingFormat format : NamingFormat.values()) {
            assertEquals(format, NamingFormat.fromSetting(format.label()).orElseThrow());
        }
        assertEquals(NamingFormat.labels().size(), NamingFormat.values().length);
    }

    @Test
    void unknownFormatValuesAreReportedRatherThanGuessed() {
        assertTrue(NamingFormat.fromSetting("Something else").isEmpty());
        assertTrue(NamingFormat.fromSetting(null).isEmpty());
        assertTrue(NamingFormat.fromSetting("").isEmpty());
    }
}
