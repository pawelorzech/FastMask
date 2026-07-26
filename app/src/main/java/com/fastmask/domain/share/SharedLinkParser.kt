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
     * Takes a [CharSequence], not a String: `Intent.EXTRA_TEXT` is declared as
     * a CharSequence and real senders (Gmail, Docs, several readers) put a
     * `Spanned` in it, so the share path hands us whatever they wrote.
     *
     * Every branch of the loop below moves [index] strictly forward by at least
     * the whole run it just examined — never by one character after a scan.
     * That is what keeps the walk O(n): the earlier `index++` fallback rescanned
     * the same host run once per character, which is quadratic and turned a
     * ~300k-char share (or a long CJK article, whose letters are all
     * `isLetterOrDigit` with no ASCII dot to stop them) into an ANR on the main
     * thread. See the timing tests in SharedLinkParserTest.
     *
     * @return null when [text] is null, blank, or carries no plausible link.
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
                // Nothing here can start a host; advance one character.
                index++
                continue
            }

            val schemelessEnd: Int? = schemelessCandidateEnd(input, index, runEnd)
            if (schemelessEnd == null) {
                // The whole run carries no dot, so no substring of it can be a
                // host either — skip past it instead of re-scanning it.
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

        val forDomain: String = host.lowercase(Locale.ROOT).removePrefix("www.")
        val url: String = if (hasScheme) candidate else "https://$candidate"
        return SharePrefill(forDomain = forDomain, url = url, description = forDomain)
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

    /**
     * End of the host-shaped run starting at [start], or [start] itself when no
     * host can start there. Separate from [schemelessCandidateEnd] so the caller
     * can skip a rejected run wholesale.
     */
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

    /**
     * @param runEnd end of the host run, from [hostRunEnd].
     * @return end of the candidate (host + optional port + optional path), or
     *   null when the run holds no dot and therefore cannot be a host.
     */
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
     * Whether a bare-host candidate at [start] is really a fragment of
     * something else.
     *
     * This used to reject on ':' as well, which killed the single most common
     * shape of shared text: "Sign up here: example.com", "Rejestracja:
     * sklep.pl". The rejection then jumped past the whole candidate, so the
     * share target opened an EMPTY create form. A colon in prose introduces a
     * link far more often than it hides one, and the case the ':' rule was
     * written for — "mailto:foo@example.com" — was never caught by it anyway
     * (the scan restarts at every character, and the run at 'e' is preceded by
     * '@', not ':').
     *
     * The '/' half stays, and it keeps the backwards whitespace walk: without
     * it "http:// spaces.example.com" — a broken link, not an endorsement of
     * spaces.example.com — silently resolves to a DIFFERENT domain than the one
     * shared, and "path/to/file.txt" resolves to a "domain" called file.txt.
     * Nothing else in prose puts a slash immediately before a host.
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
