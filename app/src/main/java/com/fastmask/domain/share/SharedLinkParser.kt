package com.fastmask.domain.share

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
 * STUB — the implementation is written against
 * `app/src/test/java/com/fastmask/domain/share/SharedLinkParserTest.kt`.
 */
object SharedLinkParser {

    /**
     * Extracts the first usable link from [text].
     *
     * @return null when [text] is null, blank, or carries no plausible link.
     */
    fun parse(text: String?): SharePrefill? =
        throw NotImplementedError("SharedLinkParser.parse is not implemented yet")
}
