package com.fastmask.ui.components.expressive

import androidx.compose.foundation.shape.CornerSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.ToggleButtonShapes
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** Space between the buttons of a connected group. */
val ConnectedGap = 2.dp

/**
 * Shapes for toggle button [index] of [count] in a connected single-select group. Every button keeps
 * full outer corners and small inner corners so the group reads as one segmented unit; the checked
 * button rounds its inner corners further (but never to a full pill, which would make two buttons
 * look like two separate pills) and presses to the smallest radius. Use the same helper for every
 * connected group (filters, status, initial state) so they are identical.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
fun connectedToggleShapes(index: Int, count: Int): ToggleButtonShapes {
    val full = CornerSize(50)
    fun shape(inner: Dp) = RoundedCornerShape(
        topStart = if (index == 0) full else CornerSize(inner),
        bottomStart = if (index == 0) full else CornerSize(inner),
        topEnd = if (index == count - 1) full else CornerSize(inner),
        bottomEnd = if (index == count - 1) full else CornerSize(inner),
    )
    return ToggleButtonShapes(
        shape = shape(INNER_REST),
        pressedShape = shape(INNER_PRESSED),
        checkedShape = shape(INNER_CHECKED),
    )
}

private val INNER_REST = 4.dp
private val INNER_PRESSED = 2.dp
private val INNER_CHECKED = 14.dp
