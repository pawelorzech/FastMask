package com.fastmask.data.repository

import com.fastmask.data.api.JmapApi
import com.fastmask.data.api.MaskedEmailDto
import com.fastmask.data.api.MaskedEmailState
import com.fastmask.data.local.MaskedEmailCache
import com.fastmask.data.local.TokenStorage
import com.fastmask.domain.model.EmailState
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import java.time.Instant
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** DTO → domain mapping: dates, states, and the not-authenticated guard. */
class MaskedEmailRepositoryImplTest {

    private val jmapApi = mockk<JmapApi>()
    private val tokenStorage = mockk<TokenStorage>()
    private val cache = mockk<MaskedEmailCache>(relaxed = true)
    private val repo = MaskedEmailRepositoryImpl(jmapApi, tokenStorage, cache)

    private fun dto(
        createdAt: String? = null,
        lastMessageAt: String? = null,
        state: MaskedEmailState = MaskedEmailState.ENABLED,
    ) = MaskedEmailDto(
        id = "m1",
        email = "a@fastmail.com",
        state = state,
        createdAt = createdAt,
        lastMessageAt = lastMessageAt,
    )

    @Test
    fun `valid iso dates are parsed to instants`() = runTest {
        every { tokenStorage.getToken() } returns "tok"
        coEvery { jmapApi.getMaskedEmails("tok") } returns Result.success(
            listOf(dto(createdAt = "2026-01-15T10:30:00Z", lastMessageAt = "2026-02-01T08:00:00Z"))
        )

        val result = repo.getMaskedEmails().getOrThrow().single()

        assertEquals(Instant.parse("2026-01-15T10:30:00Z"), result.createdAt)
        assertEquals(Instant.parse("2026-02-01T08:00:00Z"), result.lastMessageAt)
    }

    @Test
    fun `malformed date degrades to null instead of crashing`() = runTest {
        every { tokenStorage.getToken() } returns "tok"
        coEvery { jmapApi.getMaskedEmails("tok") } returns Result.success(
            listOf(dto(createdAt = "not-a-date", lastMessageAt = null))
        )

        val result = repo.getMaskedEmails().getOrThrow().single()

        assertNull(result.createdAt)
        assertNull(result.lastMessageAt)
    }

    @Test
    fun `all four jmap states map to domain states`() = runTest {
        every { tokenStorage.getToken() } returns "tok"
        coEvery { jmapApi.getMaskedEmails("tok") } returns Result.success(
            listOf(
                dto(state = MaskedEmailState.PENDING),
                dto(state = MaskedEmailState.ENABLED),
                dto(state = MaskedEmailState.DISABLED),
                dto(state = MaskedEmailState.DELETED),
            )
        )

        val states = repo.getMaskedEmails().getOrThrow().map { it.state }

        assertEquals(
            listOf(EmailState.PENDING, EmailState.ENABLED, EmailState.DISABLED, EmailState.DELETED),
            states
        )
    }

    @Test
    fun `missing token fails without touching the network`() = runTest {
        every { tokenStorage.getToken() } returns null

        val result = repo.getMaskedEmails()

        assertTrue(result.isFailure)
        // No stubbing for jmapApi.getMaskedEmails — a call would throw MockKException.
    }

    // --- offline cache write-through (B1) ----------------------------------

    @Test
    fun `a successful fetch is written through to the cache`() = runTest {
        every { tokenStorage.getToken() } returns "token"
        coEvery { jmapApi.getMaskedEmails("token") } returns Result.success(listOf(dto()))

        val result = repo.getMaskedEmails()

        assertTrue(result.isSuccess)
        verify { cache.write(match { it.single().id == "m1" }, any()) }
    }

    /** A failed fetch must not overwrite a good snapshot with nothing. */
    @Test
    fun `a failed fetch leaves the cache untouched`() = runTest {
        every { tokenStorage.getToken() } returns "token"
        coEvery { jmapApi.getMaskedEmails("token") } returns
            Result.failure(java.io.IOException("offline"))

        repo.getMaskedEmails()

        verify(exactly = 0) { cache.write(any(), any()) }
    }
}
