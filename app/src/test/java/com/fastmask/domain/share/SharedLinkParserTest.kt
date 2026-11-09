package com.fastmask.domain.share

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * The share target's whole job in one pure function.
 *
 * Whatever an app puts in an ACTION_SEND `EXTRA_TEXT` lands here: a bare link,
 * a link buried in a sentence, a "check this out" with no link at all, or
 * something that only looks like a link. The create screen must open in every
 * one of those cases — a share that crashes or that fills `forDomain` with
 * "Sign" is worse than a share that fills nothing.
 */
class SharedLinkParserTest {

    // --- the canonical case -------------------------------------------------

    @Test
    fun `bare https link yields only the Fastmail origin metadata`() {
        val prefill = SharedLinkParser.parse("https://www.github.com/signup")

        assertEquals("https://www.github.com", prefill?.forDomain)
    }

    // --- links inside prose -------------------------------------------------

    @Test
    fun `url embedded in a sentence is extracted`() {
        val prefill =
            SharedLinkParser.parse("Sign up here: https://example.com/promo and get 10% off")

        assertEquals("https://example.com", prefill?.forDomain)
    }

    @Test
    fun `sentence-ending punctuation is not part of the url`() {
        val prefill = SharedLinkParser.parse("Visit https://example.com/promo.")

        assertEquals("https://example.com", prefill?.forDomain)
    }

    @Test
    fun `the first url wins when the text carries several`() {
        val prefill =
            SharedLinkParser.parse("https://first.example.com/a then https://second.example.com/b")

        assertEquals("https://first.example.com", prefill?.forDomain)
    }

    // --- schemes ------------------------------------------------------------

    @Test
    fun `http scheme is preserved, not silently upgraded`() {
        val prefill = SharedLinkParser.parse("http://example.com/a")

        assertEquals("http://example.com", prefill?.forDomain)
    }

    @Test
    fun `schemeless link is normalized with https`() {
        val prefill = SharedLinkParser.parse("github.com/x")

        assertEquals("https://github.com", prefill?.forDomain)
    }

    // --- host shapes --------------------------------------------------------

    @Test
    fun `port stays in the origin while path is removed`() {
        val prefill = SharedLinkParser.parse("https://example.com:8443/panel")

        assertEquals("https://example.com:8443", prefill?.forDomain)
    }

    @Test
    fun `a subdomain that is not www is kept`() {
        val prefill = SharedLinkParser.parse("https://shop.example.co.uk/cart")

        assertEquals("https://shop.example.co.uk", prefill?.forDomain)
    }

    @Test
    fun `www labels are preserved as part of the origin`() {
        assertEquals("https://wwf.org", SharedLinkParser.parse("https://wwf.org/donate")?.forDomain)
        assertEquals("https://www2.example.com", SharedLinkParser.parse("https://www2.example.com/")?.forDomain)
    }

    @Test
    fun `host is lowercased so masks group by domain regardless of casing`() {
        val prefill = SharedLinkParser.parse("HTTPS://WWW.GitHub.COM/Signup")

        assertEquals("https://www.github.com", prefill?.forDomain)
    }

    // --- nothing to extract -------------------------------------------------

    @Test
    fun `plain text without a link yields null`() {
        assertNull(SharedLinkParser.parse("remember to cancel the newsletter"))
    }

    @Test
    fun `null, empty and whitespace-only text yield null`() {
        assertNull(SharedLinkParser.parse(null))
        assertNull(SharedLinkParser.parse(""))
        assertNull(SharedLinkParser.parse("   \n\t  "))
    }

    @Test
    fun `an abbreviation with a dot is not mistaken for a domain`() {
        // "e.g." would match a sloppy schemeless pattern; a one-letter TLD is
        // not a domain, and prefilling forDomain with "e.g" is user-visible junk.
        assertNull(SharedLinkParser.parse("call me, e.g. tomorrow"))
    }

    @Test
    fun `malformed links yield null rather than an empty domain`() {
        assertNull(SharedLinkParser.parse("https://"))
        assertNull(SharedLinkParser.parse("://nope"))
        assertNull(SharedLinkParser.parse("http:// spaces.example.com"))
    }

    // --- links introduced by a colon (the most common share there is) --------
    //
    // Regression: a guard that walked BACKWARDS over whitespace looking for
    // ':' or '/' rejected every bare domain that followed an introducing
    // colon, then jumped past the whole candidate — so "Rejestracja: sklep.pl",
    // the single most common shape of shared text, opened an EMPTY create form.

    @Test
    fun `a bare domain introduced by a colon is still extracted`() {
        val prefill = SharedLinkParser.parse("Sign up here: example.com/promo")

        assertEquals("https://example.com", prefill?.forDomain)
    }

    @Test
    fun `a slash still means the run is part of something else`() {
        // The other half of the same guard, kept: a bare host directly after a
        // slash is a path segment or the tail of a broken link, never a domain
        // the user meant to share. Without this, "file.txt" is a valid host
        // shape (three ASCII letters make a plausible TLD).
        assertNull(SharedLinkParser.parse("open path/to/file.txt"))
    }

    @Test
    fun `a domain lifted out of a mailto address is the registration domain`() {
        // The dropped guard was written to stop this and never did (the scan
        // restarts at every character, and the run at 'e' is preceded by '@',
        // not ':'). Pinned deliberately: example.com IS the domain the user is
        // dealing with. The parser stores it as the API's origin metadata.
        assertEquals(
            "https://example.com",
            SharedLinkParser.parse("mailto:foo@example.com")?.forDomain,
        )
    }

    // --- robustness ---------------------------------------------------------

    // The three robustness tests below guard against a parser that blows up on large input. They
    // use JUnit's `timeout` (the test body runs on its own thread and is failed when it overruns)
    // instead of reading the wall clock inside the test: the bound is two orders of magnitude
    // above the linear running time, so a loaded machine cannot flake it, and the inputs are
    // sized so a quadratic or backtracking scan overruns it by a wide margin.

    @Test(timeout = 20_000)
    fun `a very long shared text still finds a trailing link`() {
        val noise = "lorem ipsum dolor sit amet ".repeat(40_000) // ~1M chars

        val prefill = SharedLinkParser.parse(noise + "https://example.com/deal")

        assertNotNull("a link at the end of a long share must still be found", prefill)
        assertEquals("https://example.com", prefill?.forDomain)
    }

    /**
     * The share target is exported and parsing happens on the MAIN thread in
     * `onCreate`, so a sender controls both the input and when the app freezes.
     * The scan used to restart one character into a host run it had already
     * rejected, which is O(n²): 80k characters took ~1.1s on a desktop JVM and
     * several times that on a phone, i.e. a guaranteed ANR for a big enough
     * share. At a million characters the quadratic scan needs minutes, the
     * linear one milliseconds.
     */
    @Test(timeout = 20_000)
    fun `a huge run of dotless characters cannot hang the parser`() {
        val hostile = "a".repeat(1_000_000)

        assertNull(SharedLinkParser.parse(hostile))
    }

    @Test(timeout = 20_000)
    fun `a long CJK article cannot hang the parser`() {
        // Not an attack — someone sharing a Japanese article. Every CJK
        // character answers true to isLetterOrDigit, and 。is not '.', so the
        // whole text is one enormous dotless host run.
        val article = "文字化けテストの長い記事".repeat(80_000) // ~960k chars

        assertNull(SharedLinkParser.parse(article))
    }

    @Test
    fun `a link at the end of a huge dotless run is still found`() {
        // Skipping a rejected run must not skip past a real link behind it.
        val prefill = SharedLinkParser.parse("b".repeat(100_000) + " https://example.com/deal")

        assertEquals("https://example.com", prefill?.forDomain)
    }

    // --- input types --------------------------------------------------------

    @Test
    fun `a non-String CharSequence parses like the equivalent String`() {
        // EXTRA_TEXT is declared as a CharSequence and senders really do put a
        // Spanned in it. Spannable is an Android type, so the JVM stand-in here
        // is any CharSequence that is not a String — the parser must never rely
        // on the runtime class.
        val shared: CharSequence = StringBuilder("Sign up: https://www.github.com/signup")

        val prefill = SharedLinkParser.parse(shared)

        assertEquals("https://www.github.com", prefill?.forDomain)
    }
}
