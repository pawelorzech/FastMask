package com.fastmask.domain.auth

/**
 * Shape rules for a Fastmail API token, as the single source of truth for both
 * the paste action and the login submit path.
 *
 * Pure — no Android, no network. Nothing here ever leaves the device: the
 * check exists to catch the user who pastes their Fastmail *password* (or a
 * random line of text) into the token field, and it must never be turned into
 * a hard gate, because the token format is Fastmail's to change.
 */
object TokenFormat {

    /** Prefix every Fastmail API token has carried so far. */
    const val FASTMAIL_TOKEN_PREFIX = "fmu1-"

    /**
     * Strips every whitespace character (spaces, tabs, newlines, non-breaking
     * spaces) from [raw]. Clipboard content routinely arrives wrapped in them.
     */
    fun sanitize(raw: String): String = raw.filterNot { it.isWhitespace() }

    /** True when the sanitized [raw] has the recognizable Fastmail token shape. */
    fun looksLikeToken(raw: String): Boolean =
        sanitize(raw).startsWith(FASTMAIL_TOKEN_PREFIX, ignoreCase = true)

    /**
     * True when [raw] carries content that does not look like a token — the
     * trigger for a soft, non-blocking warning. Blank input is *not* warned
     * about: that case already has its own "enter your token" error.
     */
    fun shouldWarn(raw: String): Boolean {
        val sanitized = sanitize(raw)
        return sanitized.isNotEmpty() && !looksLikeToken(raw)
    }
}
