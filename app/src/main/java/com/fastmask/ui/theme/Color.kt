package com.fastmask.ui.theme

import androidx.compose.ui.graphics.Color

// Warm-ink palette — light
val LightBg = Color(0xFFF4EFE6)
val LightSurface = Color(0xFFFAF7F1)
val LightSurfaceAlt = Color(0xFFECE6D8)
val LightInk = Color(0xFF1A1714)
val LightInkSoft = Color(0xFF4A4438)
// Darkened from #8A8170 (3.36:1) to meet WCAG AA (4.5:1) for the small
// timestamp/label text this tone carries — ~4.9:1 on bg, ~5.3:1 on surface.
val LightInkMuted = Color(0xFF6A614F)
val LightLine = Color(0xFFDDD4C2)
val LightLineStrong = Color(0xFFC2B9A5)

/**
 * Border of an interactive text field, and only that.
 *
 * WCAG 1.4.11 asks for 3:1 on the boundary of a component the user has to find
 * and act on. `LightLine` gives 1.28:1 against the page and `LightLineStrong`
 * 1.70:1, so on the three screens where something has to be typed — the token,
 * a new mask, an edit — the field was effectively borderless for anyone with
 * reduced contrast sensitivity. This is 3.23:1 against the page and 3.46:1
 * against a surface.
 *
 * Separate from `LightLine` on purpose: hairline dividers and card edges are
 * decorative, they carry no affordance, and darkening all of them would repaint
 * the whole warm-ink look to fix a problem three screens have.
 */
val LightInputLine = Color(0xFF8E846E)
val LightChip = Color(0xFFE6DFCE)
val LightInputBg = Color(0xFFFFFFFF)
val LightActiveBg = Color(0xFFD9E5CF)
val LightActiveInk = Color(0xFF3A5724)
val LightOffBg = Color(0xFFE3DCC9)
// Darkened from #6B6450, which sat at 4.31:1 on LightOffBg — under the 4.5:1
// WCAG AA floor, and the "off" pill label renders at ~11sp, so it does not
// qualify for the large-text exemption. #645E4B measures 4.73:1 against the same
// background. Every other status pair already passed (light 5.8-6.3:1, dark
// 5.3-7.7:1); this was the only one below the line.
val LightOffInk = Color(0xFF645E4B)
val LightArchivedBg = Color(0xFFE8D6C9)
val LightArchivedInk = Color(0xFF7D3D1E)
val LightPendingBg = Color(0xFFF0DFB0)
val LightPendingInk = Color(0xFF6B4C0D)

// Warm-ink palette — dark
val DarkBg = Color(0xFF171513)
val DarkSurface = Color(0xFF201D19)
val DarkSurfaceAlt = Color(0xFF2A2621)
val DarkInk = Color(0xFFF0EBE1)
val DarkInkSoft = Color(0xFFC8C1B1)
// Lightened from #8A8171 (4.4:1 on surface) to a comfortable AA margin
// (~5.4:1 on surface, ~5.9:1 on bg) for small text.
val DarkInkMuted = Color(0xFF9A9181)
val DarkLine = Color(0xFF332E27)
val DarkLineStrong = Color(0xFF443D33)

/** Dark-theme counterpart of [LightInputLine]: 3.58:1 on the page, 3.30:1 on a surface. */
val DarkInputLine = Color(0xFF776D5C)
val DarkChip = Color(0xFF2D2923)
val DarkInputBg = Color(0xFF1C1916)
val DarkActiveBg = Color(0xFF2F3D25)
val DarkActiveInk = Color(0xFFB8D49A)
val DarkOffBg = Color(0xFF332E27)
val DarkOffInk = Color(0xFFA9A294)
val DarkArchivedBg = Color(0xFF3D2B20)
val DarkArchivedInk = Color(0xFFE5A480)
val DarkPendingBg = Color(0xFF3D3011)
val DarkPendingInk = Color(0xFFE6C576)

// Accent (amber). Light theme: deep burnt-amber with parchment on-accent text.
val AccentAmber = Color(0xFFA8530F)
val OnAccent = Color(0xFFFAF7F1)
// Dark theme: brightened amber so it stays legible as a FOREGROUND (cursor, icon
// tints, preview prefix, links) on dark surfaces — the deep #A8530F lands at
// ~3.4:1 on DarkBg, below AA 4.5:1 for small text. This value is ~5.3:1 on
// DarkBg / ~4.9:1 on DarkSurface, and pairs with dark ink (LightInk) as
// on-accent text on fills (~5:1), matching how the other dark accents work.
val DarkAccentAmber = Color(0xFFC9761F)
