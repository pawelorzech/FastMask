package com.fastmask.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MaskPrefixTest {

    @Test
    fun `empty and well-formed prefixes are valid`() {
        assertNull(MaskPrefix.validate(""))
        assertNull(MaskPrefix.validate("shop_2026"))
        assertNull(MaskPrefix.validate("a".repeat(MaskPrefix.MAX_LENGTH)))
    }

    @Test
    fun `over-long prefix is TOO_LONG even when it also has bad characters`() {
        assertEquals(MaskPrefix.Error.TOO_LONG, MaskPrefix.validate("a".repeat(65)))
        assertEquals(MaskPrefix.Error.TOO_LONG, MaskPrefix.validate("A".repeat(65)))
    }

    @Test
    fun `uppercase, unicode and punctuation are INVALID_CHARS`() {
        assertEquals(MaskPrefix.Error.INVALID_CHARS, MaskPrefix.validate("Shop"))
        assertEquals(MaskPrefix.Error.INVALID_CHARS, MaskPrefix.validate("café"))
        assertEquals(MaskPrefix.Error.INVALID_CHARS, MaskPrefix.validate("a-b"))
    }

    @Test
    fun `sanitize lowercases and keeps exactly what validate accepts`() {
        assertEquals("myshopname", MaskPrefix.sanitize("My.Shop-Name!"))
        assertEquals("shop_2026", MaskPrefix.sanitize("shop_2026"))
        assertEquals("caf", MaskPrefix.sanitize("café"))
        assertEquals("d", MaskPrefix.sanitize(" \u0141\u00f3d\u017a"))
        for (input in listOf("Shop", "café", "a-b", "Zażółć gęślą", "٣٤", "ＡＢ", "a b\tc")) {
            assertNull(MaskPrefix.validate(MaskPrefix.sanitize(input)))
        }
    }
}
