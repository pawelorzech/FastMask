package com.fastmask.domain.model

/**
 * Color source for the app. [DYNAMIC] takes colors from the wallpaper
 * (Material You) and is the free default; on devices below API 31 it falls back
 * to the Amber seed scheme. The remaining entries are fixed seed schemes and
 * are part of FastMask Pro. Color values live in the UI layer
 * ([com.fastmask.ui.theme]) — this enum is just the choice.
 */
enum class Accent {
    DYNAMIC,
    AMBER,
    INK,
    SAGE,
    PLUM,
    COBALT;

    /** Every accent but the wallpaper colours is part of Pro. */
    val isPro: Boolean get() = this != DYNAMIC

    /** What is actually painted: without Pro a stored seed falls back to the wallpaper, and the stored choice is kept. */
    fun shownFor(status: ProStatus): Accent = if (!isPro || status.isPro) this else DEFAULT

    companion object {
        val DEFAULT = DYNAMIC

        fun fromName(name: String?): Accent =
            entries.firstOrNull { it.name == name } ?: DEFAULT
    }
}
