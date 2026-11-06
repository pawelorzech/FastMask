package com.fastmask.domain.model

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MaskedEmailTest {

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
