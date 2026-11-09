package com.fastmask.ui.common

import androidx.compose.material3.adaptive.currentWindowAdaptiveInfo
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.layout
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.window.core.layout.WindowSizeClass

object ContentWidth {
    val List: Dp = 640.dp

    val Form: Dp = 560.dp
}

@Composable
fun isCompactHeight(): Boolean =
    !currentWindowAdaptiveInfo().windowSizeClass
        .isHeightAtLeastBreakpoint(WindowSizeClass.HEIGHT_DP_MEDIUM_LOWER_BOUND)

/** Apply AFTER `verticalScroll` so the scrollable area (and its touch target) still spans the full window width. */
fun Modifier.readableWidth(max: Dp): Modifier = layout { measurable, constraints ->
    val boundedMax = if (constraints.hasBoundedWidth) constraints.maxWidth else Constraints.Infinity
    val width = minOf(boundedMax, max.roundToPx())
    val placeable = measurable.measure(
        constraints.copy(
            minWidth = if (constraints.minWidth > 0) width else 0,
            maxWidth = width,
        ),
    )
    val outer = if (constraints.hasBoundedWidth) constraints.maxWidth else placeable.width
    layout(outer, placeable.height) {
        placeable.place((outer - placeable.width) / 2, 0)
    }
}
