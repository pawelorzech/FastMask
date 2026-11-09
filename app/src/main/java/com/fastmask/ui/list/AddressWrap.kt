package com.fastmask.ui.list

/**
 * Display form of a masked address that may only wrap before the `@`. The platform
 * line breaker would split the local part at `.`, `_` and `-`; word joiners (U+2060)
 * keep it in one piece and a zero-width space (U+200B) before `@` is the one wrap point.
 *
 * Only for display: keep the plain address as the semantics text so TalkBack and
 * copy/search read the real address.
 */
internal fun addressWrapText(address: String): String =
    addressWrapParts(address).let { it.local + it.domain }

internal class AddressWrapParts(val local: String, val domain: String)

internal fun addressWrapParts(address: String): AddressWrapParts {
    val at = address.indexOf('@')
    val local = if (at < 0) address else address.substring(0, at)
    val joined = buildString(local.length * 2) {
        local.forEach { c ->
            if (c.isLetterOrDigit()) append(c) else append(WORD_JOINER).append(c).append(WORD_JOINER)
        }
    }
    return if (at < 0) AddressWrapParts(joined, "") else AddressWrapParts(joined + ZERO_WIDTH_SPACE, address.substring(at))
}

internal const val WORD_JOINER = '⁠'
internal const val ZERO_WIDTH_SPACE = '​'
