package com.fastmask.ui.list

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AddressWrapTest {

    private fun visible(s: String) = s.replace(WORD_JOINER.toString(), "").replace(ZERO_WIDTH_SPACE.toString(), "")

    @Test
    fun `display text reads exactly like the address`() {
        listOf("sharp.flame138@fastmask.com", "my_site-x.y@fastmail.com", "nodomain").forEach {
            assertEquals(it, visible(addressWrapText(it)))
        }
    }

    @Test
    fun `the only break opportunity is right before the at sign`() {
        val display = addressWrapText("sharp.flame138@fastmask.com")
        assertEquals(1, display.count { it == ZERO_WIDTH_SPACE })
        assertEquals(ZERO_WIDTH_SPACE, display[display.indexOf('@') - 1])
        // Every punctuation mark in the local part is glued to both neighbours.
        val local = display.substring(0, display.indexOf(ZERO_WIDTH_SPACE))
        Regex("[._-]").findAll(local).forEach { m ->
            assertEquals(WORD_JOINER, local[m.range.first - 1])
            assertEquals(WORD_JOINER, local[m.range.first + 1])
        }
    }

    @Test
    fun `domain styling starts at the at sign`() {
        val display = addressWrapText("a.b@x.com")
        assertTrue(display.substring(addressWrapDomainStart(display)).startsWith("@x.com"))
        assertFalse(addressWrapText("plain").contains(ZERO_WIDTH_SPACE))
    }
}
