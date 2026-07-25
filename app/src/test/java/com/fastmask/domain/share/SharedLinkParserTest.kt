package com.fastmask.domain.share

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
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
    fun `bare https link yields domain without www, the full url and a domain description`() {
        val prefill = SharedLinkParser.parse("https://www.github.com/signup")

        assertEquals("github.com", prefill?.forDomain)
        assertEquals("https://www.github.com/signup", prefill?.url)
        assertEquals("github.com", prefill?.description)
    }

    // --- links inside prose -------------------------------------------------

    @Test
    fun `url embedded in a sentence is extracted`() {
        val prefill =
            SharedLinkParser.parse("Sign up here: https://example.com/promo and get 10% off")

        assertEquals("example.com", prefill?.forDomain)
        assertEquals("https://example.com/promo", prefill?.url)
    }

    @Test
    fun `sentence-ending punctuation is not part of the url`() {
        val prefill = SharedLinkParser.parse("Visit https://example.com/promo.")

        assertEquals("https://example.com/promo", prefill?.url)
        assertEquals("example.com", prefill?.forDomain)
    }

    @Test
    fun `the first url wins when the text carries several`() {
        val prefill =
            SharedLinkParser.parse("https://first.example.com/a then https://second.example.com/b")

        assertEquals("first.example.com", prefill?.forDomain)
        assertEquals("https://first.example.com/a", prefill?.url)
    }

    // --- schemes ------------------------------------------------------------

    @Test
    fun `http scheme is preserved, not silently upgraded`() {
        val prefill = SharedLinkParser.parse("http://example.com/a")

        assertEquals("http://example.com/a", prefill?.url)
        assertEquals("example.com", prefill?.forDomain)
    }

    @Test
    fun `schemeless link is normalized with https`() {
        val prefill = SharedLinkParser.parse("github.com/x")

        assertEquals("github.com", prefill?.forDomain)
        assertEquals("https://github.com/x", prefill?.url)
    }

    // --- host shapes --------------------------------------------------------

    @Test
    fun `port stays in the url but never in the domain`() {
        val prefill = SharedLinkParser.parse("https://example.com:8443/panel")

        assertEquals("example.com", prefill?.forDomain)
        assertEquals("https://example.com:8443/panel", prefill?.url)
    }

    @Test
    fun `a subdomain that is not www is kept`() {
        val prefill = SharedLinkParser.parse("https://shop.example.co.uk/cart")

        assertEquals("shop.example.co.uk", prefill?.forDomain)
    }

    @Test
    fun `only a leading www label is stripped, never a www prefix of the name`() {
        // Regression trap: a naive removePrefix("www") turns wwf.org into f.org.
        assertEquals("wwf.org", SharedLinkParser.parse("https://wwf.org/donate")?.forDomain)
        assertEquals("www2.example.com", SharedLinkParser.parse("https://www2.example.com/")?.forDomain)
    }

    @Test
    fun `host is lowercased so masks group by domain regardless of casing`() {
        val prefill = SharedLinkParser.parse("HTTPS://WWW.GitHub.COM/Signup")

        assertEquals("github.com", prefill?.forDomain)
        assertEquals("github.com", prefill?.description)
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

    // --- robustness ---------------------------------------------------------

    @Test
    fun `a very long shared text still finds a trailing link and returns promptly`() {
        val noise = "lorem ipsum dolor sit amet ".repeat(4_000) // ~108k chars
        val started = System.nanoTime()

        val prefill = SharedLinkParser.parse(noise + "https://example.com/deal")

        val elapsedMs = (System.nanoTime() - started) / 1_000_000
        assertNotNull("a link at the end of a long share must still be found", prefill)
        assertEquals("example.com", prefill?.forDomain)
        assertEquals("https://example.com/deal", prefill?.url)
        assertTrue("parsing took ${elapsedMs}ms — the pattern backtracks", elapsedMs < 1_000)
    }
}
