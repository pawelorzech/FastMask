package com.fastmask.ui.common

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TextPolicyTest {

    @Test
    fun `create treats blank as not set and trims the rest`() {
        assertNull("   ".trimToNull())
        assertNull("".trimToNull())
        assertEquals("a b", "  a b ".trimToNull())
    }

    @Test
    fun `detail sends only a changed value, trimmed, and empty clears`() {
        assertNull("  note ".trimmedIfChangedFrom("note"))
        assertNull("".trimmedIfChangedFrom(null))
        assertEquals("new", " new ".trimmedIfChangedFrom("note"))
        assertEquals("", "   ".trimmedIfChangedFrom("note"))
    }
}
