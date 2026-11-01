package com.fastmask.ui.list

/**
 * Display form of a masked address that may only wrap before the `@`.
 *
 * The platform line breaker treats `.`, `_` and `-` as break opportunities, so a
 * two-line row rendered "sharp" / ".flame138@fastmask…": the local part — the
 * part people recognise — was split in half. Word joiners (U+2060) around every
 * non-alphanumeric character keep the local part in one piece, and a zero-width
 * space (U+200B) before `@` gives the breaker exactly one place to wrap:
 * "sharp.flame138" / "@fastmask.com".
 *
 * Only for display. Callers must keep the plain address as the semantics text so
 * TalkBack and copy/search read the real address.
 */
internal fun addressWrapText(address: String): String {
    val at = address.indexOf('@')
    val local = if (at < 0) address else address.substring(0, at)
    val joined = buildString(local.length * 2) {
        local.forEach { c ->
            if (c.isLetterOrDigit()) append(c) else append(WORD_JOINER).append(c).append(WORD_JOINER)
        }
    }
    return if (at < 0) joined else joined + ZERO_WIDTH_SPACE + address.substring(at)
}

/** Index of `@` in the display text, so the domain can be styled separately. */
internal fun addressWrapDomainStart(display: String): Int = display.indexOf('@')

internal const val WORD_JOINER = '⁠'
internal const val ZERO_WIDTH_SPACE = '​'
