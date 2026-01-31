package com.fastmask.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

@Immutable
data class StatusColorPair(
    val container: Color,
    val content: Color
)

@Immutable
data class StatusColors(
    val enabled: StatusColorPair,
    val disabled: StatusColorPair,
    val deleted: StatusColorPair,
    val pending: StatusColorPair
)

val LightStatusColors = StatusColors(
    enabled = StatusColorPair(
        container = StatusEnabledContainerLight,
        content = StatusEnabledContentLight
    ),
    disabled = StatusColorPair(
        container = StatusDisabledContainerLight,
        content = StatusDisabledContentLight
    ),
    deleted = StatusColorPair(
        container = StatusDeletedContainerLight,
        content = StatusDeletedContentLight
    ),
    pending = StatusColorPair(
        container = StatusPendingContainerLight,
        content = StatusPendingContentLight
    )
)

val DarkStatusColors = StatusColors(
    enabled = StatusColorPair(
        container = StatusEnabledContainerDark,
        content = StatusEnabledContentDark
    ),
    disabled = StatusColorPair(
        container = StatusDisabledContainerDark,
        content = StatusDisabledContentDark
    ),
    deleted = StatusColorPair(
        container = StatusDeletedContainerDark,
        content = StatusDeletedContentDark
    ),
    pending = StatusColorPair(
        container = StatusPendingContainerDark,
        content = StatusPendingContentDark
    )
)

val LocalStatusColors = staticCompositionLocalOf { LightStatusColors }

object FastMaskStatusColors {
    val current: StatusColors
        @Composable
        get() = LocalStatusColors.current
}
