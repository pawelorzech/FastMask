package com.fastmask.domain.model

/**
 * Accent color themes. [AMBER] is the classic FastMask accent and the free
 * default; the remaining accents are part of FastMask Pro. Color values live
 * in the UI layer ([com.fastmask.ui.theme]) — this enum is just the choice.
 */
enum class Accent {
    AMBER,
    INK,
    SAGE,
    PLUM,
    COBALT;

    companion object {
        val DEFAULT = AMBER

        fun fromName(name: String?): Accent =
            entries.firstOrNull { it.name == name } ?: DEFAULT
    }
}
