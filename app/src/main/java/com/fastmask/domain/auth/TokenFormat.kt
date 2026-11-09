package com.fastmask.domain.auth

/**
 * Nothing here ever leaves the device: the check exists to catch the user who pastes their Fastmail *password* (or a
 * random line of text) into the token field, and it must never be turned into
 * a hard gate, because the token format is Fastmail's to change.
 */
object TokenFormat {

    /** Prefix every Fastmail API token has carried so far. */
    const val FASTMAIL_TOKEN_PREFIX = "fmu1-"

    /**
     * Most characters ever accepted from a single paste.
     *
     * A Fastmail token is around 40 characters, so this is pure headroom. The
     * cap exists because the clipboard is not: Android's ClipData budget
     * permits hundreds of kilobytes, and every accepted character is turned
     * into a masked glyph laid out on ONE line, on the main thread, by the
     * password field. Accepting a whole copied document there is seconds of
     * jank or an ANR.
     */
    const val MAX_PASTED_CHARS = 4096

    // Clipboard content routinely arrives wrapped in whitespace.
    fun sanitize(raw: String): String = raw.filterNot { it.isWhitespace() }

    fun sanitizePasted(raw: String): String = sanitize(raw).take(MAX_PASTED_CHARS)

    fun shouldWarn(raw: String): Boolean {
        val sanitized = sanitize(raw)
        return sanitized.isNotEmpty() &&
            !sanitized.startsWith(FASTMAIL_TOKEN_PREFIX, ignoreCase = true)
    }
}
