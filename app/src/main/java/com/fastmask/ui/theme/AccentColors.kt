package com.fastmask.ui.theme

import androidx.compose.ui.graphics.Color
import com.fastmask.domain.model.Accent

/**
 * Accent palette (FastMask Pro). One color per accent, used in both light and
 * dark themes — same convention as the classic amber. Every value keeps
 * parchment [OnAccent] text at ≥ 4.5:1 (WCAG AA): amber 5.0, ink 9.0,
 * sage 5.6, plum 7.5, cobalt 7.3.
 */
val Accent.color: Color
    get() = when (this) {
        Accent.AMBER -> AccentAmber
        Accent.INK -> Color(0xFF4A4438)
        Accent.SAGE -> Color(0xFF4E6B35)
        Accent.PLUM -> Color(0xFF7A3B5E)
        Accent.COBALT -> Color(0xFF2F5382)
    }
