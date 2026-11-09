package com.fastmask.domain.share

import com.fastmask.domain.model.MaskedEmailMetadata

/**
 * Prefill for the create screen, derived from text shared into FastMask
 * (ACTION_SEND, text/plain).
 *
 * Only [forDomain], the HTTP(S) origin Fastmail's Masked Email API expects. A
 * share never fills `url` or `description`: Fastmail reserves `url` for a deep
 * link back to the integrator's record, and `description` must be short,
 * user-supplied and must not repeat the domain.
 */
data class SharePrefill(val forDomain: String)

object SharedLinkParser {
    private val trailingPunctuation: Set<Char> = setOf('.', ',', ';', ':', '!', '?', ')', ']', '}', '"', '\'')

    /**
     * Takes a [CharSequence], not a String: `Intent.EXTRA_TEXT` is declared as
     * a CharSequence and real senders (Gmail, Docs, several readers) put a
     * `Spanned` in it.
     *
     * Every branch of the loop moves [index] strictly forward by at least the
     * whole run it just examined: rescanning a host run per character is
     * quadratic and turns a ~300k-char share (or a long CJK article, whose letters
     * are all `isLetterOrDigit` with no ASCII dot to stop them) into an ANR on the
     * main thread.
     */
    fun parse(text: CharSequence?): SharePrefill? {
        val input: CharSequence = text?.takeIf { it.isNotBlank() } ?: return null
        var index: Int = 0

        while (index < input.length) {
            val schemeEnd: Int? = schemeCandidateEnd(input, index)
            if (schemeEnd != null) {
                val prefill: SharePrefill? = prefillFromCandidate(
                    rawCandidate = input.substring(index, schemeEnd),
                    hasScheme = true,
                )
                if (prefill != null) {
                    return prefill
                }
                index = schemeEnd
                continue
            }

            val runEnd: Int = hostRunEnd(input, index)
            if (runEnd == index) {
                index++
                continue
            }

            val schemelessEnd: Int? = schemelessCandidateEnd(input, index, runEnd)
            if (schemelessEnd == null) {
                index = runEnd
                continue
            }

            val prefill: SharePrefill? = if (hasForbiddenSchemelessPrefix(input, index)) {
                null
            } else {
                prefillFromCandidate(
                    rawCandidate = input.substring(index, schemelessEnd),
                    hasScheme = false,
                )
            }
            if (prefill != null) {
                return prefill
            }
            index = schemelessEnd
        }

        return null
    }

    private fun prefillFromCandidate(rawCandidate: String, hasScheme: Boolean): SharePrefill? {
        val candidate: String = rawCandidate.trimEnd { it in trailingPunctuation }
        if (candidate.isEmpty()) {
            return null
        }

        val authority: String = extractAuthority(candidate, hasScheme) ?: return null
        val host: String = extractHost(authority) ?: return null
        if (!isValidHost(host)) {
            return null
        }

        val pageUrl: String = if (hasScheme) candidate else "https://$candidate"
        val forDomain = MaskedEmailMetadata.normalizeForDomain(pageUrl) ?: return null
        return SharePrefill(forDomain = forDomain)
    }

    private fun schemeCandidateEnd(text: CharSequence, start: Int): Int? {
        val schemeLength: Int = when {
            text.regionMatches(start, "https://", 0, 8, ignoreCase = true) -> 8
            text.regionMatches(start, "http://", 0, 7, ignoreCase = true) -> 7
            else -> return null
        }
        var end: Int = start + schemeLength
        while (end < text.length && isUrlRunChar(text[end])) {
            end++
        }
        return end
    }

    private fun hostRunEnd(text: CharSequence, start: Int): Int {
        if (!text[start].isLetterOrDigit()) {
            return start
        }
        var cursor: Int = start
        while (cursor < text.length && isHostRunChar(text[cursor])) {
            cursor++
        }
        return cursor
    }

    private fun schemelessCandidateEnd(text: CharSequence, start: Int, runEnd: Int): Int? {
        var sawDot: Boolean = false
        for (cursor in start until runEnd) {
            if (text[cursor] == '.') {
                sawDot = true
                break
            }
        }
        if (!sawDot) {
            return null
        }

        var end: Int = runEnd
        if (end < text.length && text[end] == ':') {
            var portCursor: Int = end + 1
            while (portCursor < text.length && text[portCursor].isDigit()) {
                portCursor++
            }
            if (portCursor > end + 1) {
                end = portCursor
            }
        }
        if (end < text.length && text[end] == '/') {
            var pathCursor: Int = end + 1
            while (pathCursor < text.length && isUrlRunChar(text[pathCursor])) {
                pathCursor++
            }
            end = pathCursor
        }
        return end
    }

    /**
     * A ':' is deliberately not a reason to reject: "Sign up here: example.com"
     * is the commonest shape of shared text, and a colon in prose introduces a
     * link far more often than it hides one.
     *
     * The '/' check stays, with the backwards whitespace walk: without it
     * "http:// spaces.example.com" (a broken link) silently resolves to a
     * DIFFERENT domain than the one shared, and "path/to/file.txt" resolves to a
     * "domain" called file.txt. Nothing else in prose puts a slash immediately
     * before a host.
     */
    private fun hasForbiddenSchemelessPrefix(text: CharSequence, start: Int): Boolean {
        var cursor: Int = start - 1
        while (cursor >= 0 && text[cursor].isWhitespace()) {
            cursor--
        }
        if (cursor < 0) {
            return false
        }
        return text[cursor] == '/'
    }

    private fun extractAuthority(candidate: String, hasScheme: Boolean): String? {
        val authorityStart: Int = if (hasScheme) {
            val separator: Int = candidate.indexOf("://")
            if (separator < 0) {
                return null
            }
            separator + 3
        } else {
            0
        }
        if (authorityStart >= candidate.length) {
            return null
        }

        var authorityEnd: Int = authorityStart
        while (authorityEnd < candidate.length) {
            when (candidate[authorityEnd]) {
                '/', '?', '#' -> break
                else -> authorityEnd++
            }
        }
        if (authorityEnd <= authorityStart) {
            return null
        }
        return candidate.substring(authorityStart, authorityEnd)
    }

    private fun extractHost(authority: String): String? {
        val withoutUserInfo: String = authority.substringAfterLast('@', authority)
        if (withoutUserInfo.isEmpty()) {
            return null
        }
        val host: String = withoutUserInfo.substringBefore(':')
        return host.takeIf { it.isNotEmpty() }
    }

    private fun isValidHost(host: String): Boolean {
        val labels: List<String> = host.split('.')
        if (labels.size < 2 || labels.any { it.isEmpty() || !it.all(::isHostLabelChar) }) {
            return false
        }

        val topLevelLabel: String = labels.last()
        return topLevelLabel.length >= 2 && topLevelLabel.all(::isAsciiLetter)
    }

    private fun isHostRunChar(char: Char): Boolean =
        char.isLetterOrDigit() || char == '-' || char == '.'

    private fun isHostLabelChar(char: Char): Boolean =
        char.isLetterOrDigit() || char == '-'

    private fun isUrlRunChar(char: Char): Boolean =
        !char.isWhitespace() && char != '<' && char != '>' && char != '"' && char != '\''

    private fun isAsciiLetter(char: Char): Boolean =
        char in 'a'..'z' || char in 'A'..'Z'
}
