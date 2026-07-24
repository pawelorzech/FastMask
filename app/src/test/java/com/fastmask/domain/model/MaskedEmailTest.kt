package com.fastmask.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MaskedEmailTest {

    @Test
    fun `displayName prefers description over domain`() {
        val email = MaskedEmail(
            id = "m1",
            email = "m1@fastmail.com",
            state = EmailState.ENABLED,
            forDomain = "example.com",
            description = "My newsletter",
            createdBy = null, url = null, emailPrefix = null,
            createdAt = null, lastMessageAt = null,
        )
        assertEquals("My newsletter", email.displayName)
    }

    @Test
    fun `displayName falls back to forDomain when description is blank`() {
        val email = MaskedEmail(
            id = "m1",
            email = "m1@fastmail.com",
            state = EmailState.ENABLED,
            forDomain = "example.com",
            description = "  ",
            createdBy = null, url = null, emailPrefix = null,
            createdAt = null, lastMessageAt = null,
        )
        assertEquals("example.com", email.displayName)
    }

    @Test
    fun `displayName falls back to email prefix when both are blank`() {
        val email = MaskedEmail(
            id = "m1",
            email = "quiet.harbor123@fastmail.com",
            state = EmailState.ENABLED,
            forDomain = null,
            description = null,
            createdBy = null, url = null, emailPrefix = null,
            createdAt = null, lastMessageAt = null,
        )
        assertEquals("quiet.harbor123", email.displayName)
    }

    @Test
    fun `isActive is true for ENABLED`() {
        val email = MaskedEmail(
            id = "m1", email = "m1@fastmail.com",
            state = EmailState.ENABLED,
            forDomain = null, description = null, createdBy = null,
            url = null, emailPrefix = null, createdAt = null, lastMessageAt = null,
        )
        assertTrue(email.isActive)
    }

    @Test
    fun `isActive is true for PENDING`() {
        val email = MaskedEmail(
            id = "m1", email = "m1@fastmail.com",
            state = EmailState.PENDING,
            forDomain = null, description = null, createdBy = null,
            url = null, emailPrefix = null, createdAt = null, lastMessageAt = null,
        )
        assertTrue(email.isActive)
    }

    @Test
    fun `isActive is false for DISABLED`() {
        val email = MaskedEmail(
            id = "m1", email = "m1@fastmail.com",
            state = EmailState.DISABLED,
            forDomain = null, description = null, createdBy = null,
            url = null, emailPrefix = null, createdAt = null, lastMessageAt = null,
        )
        assertFalse(email.isActive)
    }

    @Test
    fun `isActive is false for DELETED`() {
        val email = MaskedEmail(
            id = "m1", email = "m1@fastmail.com",
            state = EmailState.DELETED,
            forDomain = null, description = null, createdBy = null,
            url = null, emailPrefix = null, createdAt = null, lastMessageAt = null,
        )
        assertFalse(email.isActive)
    }
}
