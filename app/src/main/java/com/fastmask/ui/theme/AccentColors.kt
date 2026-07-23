package com.fastmask.ui.theme

import androidx.compose.ui.graphics.Color
import com.fastmask.domain.model.Accent

/**
 * Accent palette (FastMask Pro), theme-aware. The light theme keeps the deep
 * ink-family values (parchment [OnAccent] text at ≥ 4.5:1 WCAG AA: amber 5.0,
 * ink 9.0, sage 5.6, plum 7.5, cobalt 7.3). The dark theme brightens each
 * accent so it stays legible as a FOREGROUND (cursor, icon tints, text links,
 * FAB) on dark surfaces — the deep values land at 1.7–2.8:1 on
 * [DarkSurface], below even the 3:1 non-text minimum. Every dark variant is
 * ≥ 6.3:1 on [DarkSurface]/[DarkBg], with dark-ink on-accent text ≥ 5.9:1.
 * Amber is the classic default and intentionally identical in both themes.
 */
fun Accent.color(darkTheme: Boolean): Color = when (this) {
    Accent.AMBER -> AccentAmber
    Accent.INK -> if (darkTheme) Color(0xFFB5AC9A) else Color(0xFF4A4438)
    Accent.SAGE -> if (darkTheme) Color(0xFF9DBB79) else Color(0xFF4E6B35)
    Accent.PLUM -> if (darkTheme) Color(0xFFD294B4) else Color(0xFF7A3B5E)
    Accent.COBALT -> if (darkTheme) Color(0xFF8FB3DC) else Color(0xFF2F5382)
}

/**
 * Text/icon color on top of [color] fills (pill buttons, FAB, selected filter
 * pills). Parchment on the deep light-theme values, dark ink on the brightened
 * dark-theme variants.
 */
fun Accent.onColor(darkTheme: Boolean): Color = when {
    this == Accent.AMBER -> OnAccent
    darkTheme -> LightInk
    else -> OnAccent
}
