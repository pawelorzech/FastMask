package com.fastmask.domain.model

/** [DYNAMIC] is the free default; the remaining entries are part of FastMask Pro. */
enum class Accent {
    DYNAMIC,
    AMBER,
    INK,
    SAGE,
    PLUM,
    COBALT;

    val isPro: Boolean get() = this != DYNAMIC

    fun isLockedFor(hasPro: Boolean): Boolean = isPro && !hasPro

    /** What is actually painted: without Pro a stored seed falls back to the wallpaper, and the stored choice is kept. */
    fun shownFor(status: ProStatus): Accent = if (isLockedFor(status.isPro)) DEFAULT else this

    companion object {
        val DEFAULT = DYNAMIC

        fun fromName(name: String?): Accent =
            entries.firstOrNull { it.name == name } ?: DEFAULT
    }
}
