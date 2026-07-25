package com.fastmask.domain.share

import java.util.Locale

/**
 * Prefill for the create screen, derived from text shared into FastMask
 * (ACTION_SEND, text/plain).
 *
 * @param forDomain host with a leading `www.` label removed, lowercased.
 * @param url the full link as it appeared in the shared text; a schemeless
 *   match is normalized with an `https://` prefix.
 * @param description defaults to [forDomain] — the most useful label a share
 *   can offer without asking the user anything.
 */
data class SharePrefill(
    val forDomain: String,
    val url: String,
    val description: String,
)

/**
 * Pure Kotlin, no Android dependencies, so it is unit-testable in `src/test`.
 *
 * The parser walks shared text left-to-right, returns the FIRST usable link,
 * preserves the original URL text (except for trimmed trailing sentence
 * punctuation), and derives `forDomain`/`description` from a validated host.
 */
object SharedLinkParser {
    private val trailingPunctuation: Set<Char> = setOf('.', ',', ';', ':', '!', '?', ')', ']', '}', '"', '\'')

    /**
     * Extracts the first usable link from [text].
     *
     * @return null when [text] is null, blank, or carries no plausible link.
     */
    fun parse(text: String?): SharePrefill? {
        val input: String = text?.takeIf { it.isNotBlank() } ?: return null
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

            val schemelessEnd: Int? = schemelessCandidateEnd(input, index)
            if (schemelessEnd != null) {
                val prefill: SharePrefill? =
                    if (hasForbiddenSchemelessPrefix(input, index)) {
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
                continue
            }

            index++
        }

        return null
    }

    private fun prefillFromCandidate(rawCandidate: String, hasScheme: Boolean): SharePrefill? {
        var candidate: String = rawCandidate
        while (candidate.isNotEmpty() && candidate.last() in trailingPunctuation) {
            candidate = candidate.dropLast(1)
        }
        if (candidate.isEmpty()) {
            return null
        }

        val authority: String = extractAuthority(candidate, hasScheme) ?: return null
        val host: String = extractHost(authority) ?: return null
        if (!isValidHost(host)) {
            return null
        }

        val normalizedHost: String = host.lowercase(Locale.ROOT)
        val forDomain: String = if (normalizedHost.startsWith("www.")) {
            normalizedHost.removePrefix("www.")
        } else {
            normalizedHost
        }
        val url: String = if (hasScheme) candidate else "https://$candidate"
        return SharePrefill(forDomain = forDomain, url = url, description = forDomain)
    }

    private fun schemeCandidateEnd(text: String, start: Int): Int? {
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

    private fun schemelessCandidateEnd(text: String, start: Int): Int? {
        if (!text[start].isLetterOrDigit()) {
            return null
        }

        var cursor: Int = start
        var sawDot: Boolean = false
        while (cursor < text.length && isHostRunChar(text[cursor])) {
            if (text[cursor] == '.') {
                sawDot = true
            }
            cursor++
        }
        if (!sawDot) {
            return null
        }

        var end: Int = cursor
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

    private fun hasForbiddenSchemelessPrefix(text: String, start: Int): Boolean {
        var cursor: Int = start - 1
        while (cursor >= 0 && text[cursor].isWhitespace()) {
            cursor--
        }
        if (cursor < 0) {
            return false
        }
        return text[cursor] == ':' || text[cursor] == '/'
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

    /**
     * Compared char-by-char rather than against a set literal: a `setOf(…)`
     * here allocates once per character scanned, and the long-share test walks
     * ~108k of them.
     */
    private fun isUrlRunChar(char: Char): Boolean =
        !char.isWhitespace() && char != '<' && char != '>' && char != '"' && char != '\''

    private fun isAsciiLetter(char: Char): Boolean =
        char in 'a'..'z' || char in 'A'..'Z'
}
