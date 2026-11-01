package com.fastmask.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

@Immutable
data class StatusColorPair(
    val container: Color,
    val content: Color,
)

/**
 * Per-state container/content pairs, derived from the active color scheme.
 *
 * Migration shim: remove once screens read MaterialTheme roles directly.
 */
@Immutable
data class StatusColors(
    val enabled: StatusColorPair,
    val disabled: StatusColorPair,
    val deleted: StatusColorPair,
    val pending: StatusColorPair,
)

/**
 * Legacy extra colors, all derived from the active color scheme.
 *
 * Migration shim: remove once screens read MaterialTheme roles directly.
 */
@Immutable
data class FastMaskExtraColors(
    val inkMuted: Color,
    val inkSoft: Color,
    val lineStrong: Color,
    val chip: Color,
    val inputBg: Color,
    val inputLine: Color,
    val surfaceAlt: Color,
    val accent: Color,
    val onAccent: Color,
    val status: StatusColors,
)

internal fun ColorScheme.toStatusColors() = StatusColors(
    enabled = StatusColorPair(primaryContainer, onPrimaryContainer),
    pending = StatusColorPair(tertiaryContainer, onTertiaryContainer),
    disabled = StatusColorPair(surfaceContainerHighest, onSurfaceVariant),
    deleted = StatusColorPair(errorContainer, onErrorContainer),
)

internal fun ColorScheme.toExtras(status: StatusColors = toStatusColors()) = FastMaskExtraColors(
    inkMuted = onSurfaceVariant,
    inkSoft = onSurfaceVariant,
    lineStrong = outline,
    chip = surfaceContainerHighest,
    inputBg = surfaceContainerHigh,
    inputLine = outline,
    surfaceAlt = surfaceContainer,
    accent = primary,
    onAccent = onPrimary,
    status = status,
)

val LocalStatusColors = staticCompositionLocalOf { AmberLight.toStatusColors() }
val LocalFastMaskExtras = staticCompositionLocalOf { AmberLight.toExtras() }

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
