package com.fastmask.ui.components.expressive

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SegmentedShapeTest {
    private val o = 20.dp
    private val i = 4.dp

    @Test
    fun `single row is large everywhere`() {
        assertEquals(RoundedCornerShape(o), segmentedShape(0, 1))
    }

    @Test
    fun `first row has large top corners`() {
        assertEquals(RoundedCornerShape(topStart = o, topEnd = o, bottomEnd = i, bottomStart = i), segmentedShape(0, 3))
    }

    @Test
    fun `middle row is small everywhere`() {
        assertEquals(RoundedCornerShape(i), segmentedShape(1, 3))
    }

    @Test
    fun `last row has large bottom corners`() {
        assertEquals(RoundedCornerShape(topStart = i, topEnd = i, bottomEnd = o, bottomStart = o), segmentedShape(2, 3))
    }

    @Test
    fun `avatar shape pick is deterministic and in range`() {
        val seeds = listOf("github.com", "", "a@b.c", "ünï.cöde")
        seeds.forEach {
            val a = maskAvatarShapeIndex(it, 8)
            assertEquals(a, maskAvatarShapeIndex(it, 8))
            assertTrue(a in 0 until 8)
        }
    }

    @Test
    fun `avatar initial skips punctuation and falls back`() {
        assertEquals("G", maskAvatarInitial("github.com"))
        assertEquals("A", maskAvatarInitial("  .a@b.c"))
        assertEquals("?", maskAvatarInitial(""))
    }
}

class StateShapeBoundsTest {
    @Test
    fun `state polygons are normalized to a unit box`() {
        com.fastmask.domain.model.EmailState.entries.forEach {
            val b = polygonFor(it).calculateBounds()
            assertTrue("$it $b", b[0] >= -0.01f && b[1] >= -0.01f && b[2] <= 1.01f && b[3] <= 1.01f)
        }
    }
}
