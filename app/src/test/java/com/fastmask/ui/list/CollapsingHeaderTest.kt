package com.fastmask.ui.list

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import org.junit.Assert.assertEquals
import org.junit.Test

class CollapsingHeaderTest {

    @Test
    fun `expand restores collapsed header offset to zero`() {
        val state = CollapsingHeaderState().apply { heightPx = 200f }

        val consumed = state.connection.onPreScroll(
            available = Offset(0f, -150f),
            source = NestedScrollSource.UserInput,
        )
        assertEquals(Offset(0f, -150f), consumed)
        assertEquals(-150f, state.offsetPx, 0.01f)

        state.expand()
        assertEquals(0f, state.offsetPx, 0.01f)
    }
}
