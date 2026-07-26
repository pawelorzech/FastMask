package com.fastmask.domain.auth

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Shape rules for a pasted Fastmail API token.
 *
 * Two jobs. [TokenFormat.sanitize] is the single cleaning routine shared by
 * the paste button and the login submit path — one copy, so the two can never
 * disagree about what was sent. [TokenFormat.shouldWarn] drives a soft warning
 * whose real target is the user who pastes their Fastmail *password* into the
 * token field; it is advisory only and never blocks a login attempt.
 */
class TokenFormatTest {

    // --- sanitize ----------------------------------------------------------

    @Test
    fun `sanitize strips spaces newlines and tabs`() {
        assertEquals("fmu1-abcdef", TokenFormat.sanitize("  fmu1-abc\n def\t"))
    }

    /**
     * Clipboard text copied out of a browser routinely carries non-breaking
     * spaces. They are invisible, and a token carrying one is rejected by the
     * server with a bare 401 the user cannot explain.
     */
    @Test
    fun `sanitize strips non-breaking spaces`() {
        assertEquals("fmu1-abc", TokenFormat.sanitize("fmu1-a\u00A0bc"))
    }

    @Test
    fun `sanitize of blank input is empty`() {
        assertEquals("", TokenFormat.sanitize("  \n\t "))
    }

    @Test
    fun `sanitize leaves a clean token untouched`() {
        assertEquals("fmu1-abc123", TokenFormat.sanitize("fmu1-abc123"))
    }

    // --- recognizing the token shape ---------------------------------------

    @Test
    fun `a value carrying the fastmail prefix is recognized`() {
        assertTrue(TokenFormat.looksLikeToken("fmu1-8f2c1d9e4a"))
    }

    @Test
    fun `a token wrapped in whitespace is recognized after cleaning`() {
        assertTrue(TokenFormat.looksLikeToken("\n  fmu1-8f2c1d9e4a  \n"))
    }

    /**
     * Lenient on case: the warning is advisory, so a false alarm costs more
     * than a missed one.
     */
    @Test
    fun `the prefix is matched case insensitively`() {
        assertTrue(TokenFormat.looksLikeToken("FMU1-8F2C1D9E4A"))
    }

    /** It is a *prefix*, not a substring — "contains" would match noise. */
    @Test
    fun `the prefix must start the value`() {
        assertFalse(TokenFormat.looksLikeToken("token=fmu1-8f2c1d9e4a"))
    }

    @Test
    fun `arbitrary text is not a token`() {
        assertFalse(TokenFormat.looksLikeToken("correct horse battery staple"))
    }

    @Test
    fun `empty input is not a token`() {
        assertFalse(TokenFormat.looksLikeToken(""))
    }

    // --- the soft warning --------------------------------------------------

    /** The case worth catching: a password pasted into the token field. */
    @Test
    fun `something that looks like a password warns`() {
        assertTrue(TokenFormat.shouldWarn("Tr0ub4dor&3"))
    }

    @Test
    fun `arbitrary prose warns`() {
        assertTrue(TokenFormat.shouldWarn("my fastmail password"))
    }

    @Test
    fun `a real token does not warn`() {
        assertFalse(TokenFormat.shouldWarn("fmu1-8f2c1d9e4a"))
    }

    @Test
    fun `a token wrapped in whitespace does not warn`() {
        assertFalse(TokenFormat.shouldWarn("  fmu1-8f2c1d9e4a\n"))
    }

    /** Empty input has its own dedicated error; warning twice is noise. */
    @Test
    fun `empty input does not warn`() {
        assertFalse(TokenFormat.shouldWarn(""))
    }

    @Test
    fun `whitespace only input does not warn`() {
        assertFalse(TokenFormat.shouldWarn("   \n "))
    }
}
