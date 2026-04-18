package com.fastmask.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

@Immutable
data class StatusColorPair(
    val container: Color,
    val content: Color,
)

@Immutable
data class StatusColors(
    val enabled: StatusColorPair,
    val disabled: StatusColorPair,
    val deleted: StatusColorPair,
    val pending: StatusColorPair,
)

@Immutable
data class FastMaskExtraColors(
    val inkMuted: Color,
    val inkSoft: Color,
    val lineStrong: Color,
    val chip: Color,
    val inputBg: Color,
    val surfaceAlt: Color,
    val accent: Color,
    val onAccent: Color,
    val status: StatusColors,
)

val LightStatusColors = StatusColors(
    enabled = StatusColorPair(LightActiveBg, LightActiveInk),
    disabled = StatusColorPair(LightOffBg, LightOffInk),
    deleted = StatusColorPair(LightArchivedBg, LightArchivedInk),
    pending = StatusColorPair(LightPendingBg, LightPendingInk),
)

val DarkStatusColors = StatusColors(
    enabled = StatusColorPair(DarkActiveBg, DarkActiveInk),
    disabled = StatusColorPair(DarkOffBg, DarkOffInk),
    deleted = StatusColorPair(DarkArchivedBg, DarkArchivedInk),
    pending = StatusColorPair(DarkPendingBg, DarkPendingInk),
)

val LightExtras = FastMaskExtraColors(
    inkMuted = LightInkMuted,
    inkSoft = LightInkSoft,
    lineStrong = LightLineStrong,
    chip = LightChip,
    inputBg = LightInputBg,
    surfaceAlt = LightSurfaceAlt,
    accent = AccentAmber,
    onAccent = OnAccent,
    status = LightStatusColors,
)

val DarkExtras = FastMaskExtraColors(
    inkMuted = DarkInkMuted,
    inkSoft = DarkInkSoft,
    lineStrong = DarkLineStrong,
    chip = DarkChip,
    inputBg = DarkInputBg,
    surfaceAlt = DarkSurfaceAlt,
    accent = AccentAmber,
    onAccent = OnAccent,
    status = DarkStatusColors,
)

val LocalStatusColors = staticCompositionLocalOf { LightStatusColors }
val LocalFastMaskExtras = staticCompositionLocalOf { LightExtras }

object FastMaskStatusColors {
    val current: StatusColors
        @Composable
        get() = LocalStatusColors.current
}

object FastMaskExtras {
    val current: FastMaskExtraColors
        @Composable
        get() = LocalFastMaskExtras.current
}
