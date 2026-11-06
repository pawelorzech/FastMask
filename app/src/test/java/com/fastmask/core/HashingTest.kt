package com.fastmask.core

import org.junit.Assert.assertEquals
import org.junit.Test
import java.security.MessageDigest

class HashingTest {

    @Test
    fun `matches the published SHA-256 test vectors`() {
        assertEquals("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad", "abc".sha256Hex())
        assertEquals("e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855", "".sha256Hex())
    }

    @Test
    fun `hashes the UTF-8 bytes and stays lower-case hex`() {
        val text = "zażółć gęślą jaźń"
        val expected = MessageDigest.getInstance("SHA-256")
            .digest(text.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }

        assertEquals(expected, text.sha256Hex())
        assertEquals(64, text.sha256Hex().length)
    }
}
