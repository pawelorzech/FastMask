package com.fastmask.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MaskedEmailMetadataTest {

    @Test
    fun `bare host becomes an https origin`() {
        assertEquals("https://example.com", MaskedEmailMetadata.normalizeForDomain("example.com"))
    }

    @Test
    fun `page url is reduced to its origin`() {
        assertEquals(
            "https://www.example.com:8443",
            MaskedEmailMetadata.normalizeForDomain("https://WWW.Example.com:8443/account/new?q=1#form"),
        )
    }

    @Test
    fun `explicit http origin is preserved`() {
        assertEquals("http://example.com", MaskedEmailMetadata.normalizeForDomain("http://example.com/path"))
    }

    @Test
    fun `empty string remains an explicit clear operation`() {
        assertEquals("", MaskedEmailMetadata.normalizeForDomain("  "))
    }

    @Test
    fun `non web schemes and malformed hosts are rejected`() {
        assertNull(MaskedEmailMetadata.normalizeForDomain("mailto:user@example.com"))
        assertNull(MaskedEmailMetadata.normalizeForDomain("not a host"))
        assertNull(MaskedEmailMetadata.normalizeForDomain("https://user@example.com"))
    }
}
