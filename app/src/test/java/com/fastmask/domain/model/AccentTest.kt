package com.fastmask.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

class AccentTest {
    @Test
    fun `dynamic is the first entry and the default`() {
        assertSame(Accent.DYNAMIC, Accent.entries.first())
        assertSame(Accent.DYNAMIC, Accent.DEFAULT)
    }

    @Test
    fun `null and unknown names fall back to dynamic`() {
        assertSame(Accent.DYNAMIC, Accent.fromName(null))
        assertSame(Accent.DYNAMIC, Accent.fromName("TEAL"))
        assertSame(Accent.DYNAMIC, Accent.fromName(""))
    }

    @Test
    fun `stored seed names still resolve`() {
        listOf("AMBER", "INK", "SAGE", "PLUM", "COBALT", "DYNAMIC").forEach {
            assertEquals(it, Accent.fromName(it).name)
        }
    }
}
