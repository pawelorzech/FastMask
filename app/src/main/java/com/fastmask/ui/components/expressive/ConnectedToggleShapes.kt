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
 * look like two separate pills) and presses to the smallest radius. [vertical] is the stacked
 * layout used at large font scales. Use the same helper for every
 * connected group (filters, status, initial state) so they are identical.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
fun connectedToggleShapes(index: Int, count: Int, vertical: Boolean = false): ToggleButtonShapes {
    val full = CornerSize(50)
    val first = index == 0
    val last = index == count - 1
    fun shape(inner: Dp): RoundedCornerShape {
        val i = CornerSize(inner)
        // Stacked (vertical) groups round the top of the first and the bottom of the last.
        return if (vertical) {
            RoundedCornerShape(
                topStart = if (first) full else i,
                topEnd = if (first) full else i,
                bottomStart = if (last) full else i,
                bottomEnd = if (last) full else i,
            )
        } else {
            RoundedCornerShape(
                topStart = if (first) full else i,
                bottomStart = if (first) full else i,
                topEnd = if (last) full else i,
                bottomEnd = if (last) full else i,
            )
        }
    }
    return ToggleButtonShapes(
        shape = shape(INNER_REST),
        pressedShape = shape(INNER_PRESSED),
        checkedShape = shape(INNER_CHECKED),
    )
}

private val INNER_REST = 4.dp
private val INNER_PRESSED = 2.dp
private val INNER_CHECKED = 14.dp
