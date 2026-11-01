package com.fastmask.ui.list

import androidx.compose.foundation.gestures.DraggableState
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.layout.layout
import kotlin.math.roundToInt

/**
 * Lets a block of chrome (search + filters) scroll away with the list instead of
 * staying pinned, for windows too short to spare the height — phones in
 * landscape. It sits above the list in the same column, so it survives the list
 * switching to an empty/no-match state (the search field keeps its focus), which
 * making it a list item would not.
 *
 * Scrolling the list up collapses the header first; any scroll back down shows it
 * again before the list moves ("enter always"). Nothing is consumed horizontally.
 */
@Stable
internal class CollapsingHeaderState {
    private var heightPx by mutableFloatStateOf(0f)

    /** 0 = fully shown, -heightPx = fully collapsed. */
    var offsetPx by mutableFloatStateOf(0f)
        private set

    /**
     * Dragging the header itself moves it too. Without this, with the header fully
     * shown and only a sliver of list visible, the only way to scroll would be to
     * hit that sliver.
     */
    val dragState = DraggableState { delta -> shiftBy(delta) }

    private fun shiftBy(delta: Float): Float {
        val next = (offsetPx + delta).coerceIn(-heightPx, 0f)
        val consumed = next - offsetPx
        offsetPx = next
        return consumed
    }

    val connection = object : NestedScrollConnection {
        override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
            if (heightPx <= 0f || available.y == 0f) return Offset.Zero
            return Offset(0f, shiftBy(available.y))
        }
    }

    fun Modifier.collapsing(): Modifier = this
        .clipToBounds()
        .layout { measurable, constraints ->
            val placeable = measurable.measure(constraints)
            heightPx = placeable.height.toFloat()
            offsetPx = offsetPx.coerceIn(-heightPx, 0f)
            val visible = (placeable.height + offsetPx).roundToInt().coerceAtLeast(0)
            layout(placeable.width, visible) { placeable.place(0, offsetPx.roundToInt()) }
        }
}
