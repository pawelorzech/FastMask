package com.fastmask.ui.components.expressive

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SegmentedShapeTest {

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
