package com.fastmask.data.repository

import com.fastmask.data.api.JmapApi
import com.fastmask.data.api.MaskedEmailDto
import com.fastmask.data.api.MaskedEmailState
import com.fastmask.data.api.MaskedEmailUpdate
import com.fastmask.data.local.MaskedEmailCache
import com.fastmask.data.local.TokenStorage
import com.fastmask.domain.model.EmailState
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
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
        // The owner marker must be present: a snapshot without one is read back
        // as "not mine" and the offline list silently stays empty.
        verify {
            cache.write(
                match { it.single().id == "m1" },
                match { owner -> !owner.isNullOrBlank() },
                any(),
            )
        }
    }

    /*
     * Audit 2026-07-27. The snapshot had no owner and lived under a fixed file
     * name, so the only thing separating one account's masks from another's was
     * an unchecked `File.delete()` on sign-out. These pin the binding.
     */

    @Test
    fun `the cache is read back under the same owner it was written with`() = runTest {
        every { tokenStorage.getToken() } returns "token"
        coEvery { jmapApi.getMaskedEmails("token") } returns Result.success(listOf(dto()))
        val written = slot<String>()
        every { cache.write(any(), capture(written), any()) } returns Unit
        val readWith = slot<String>()
        every { cache.read(capture(readWith)) } returns null

        repo.getMaskedEmails()
        repo.cachedMaskedEmails()

        assertEquals(written.captured, readWith.captured)
    }

    @Test
    fun `a different token asks the cache for a different owner`() = runTest {
        val owners = mutableListOf<String?>()
        // captureNullable, not capture: the owner parameter is String?, and
        // capture()'s type variable is bounded by Any.
        every { cache.write(any(), captureNullable(owners), any()) } returns Unit
        coEvery { jmapApi.getMaskedEmails(any()) } returns Result.success(listOf(dto()))

        every { tokenStorage.getToken() } returns "account-a-token"
        repo.getMaskedEmails()
        every { tokenStorage.getToken() } returns "account-b-token"
        repo.getMaskedEmails()

        assertEquals(2, owners.size)
        assertTrue("two accounts must not share an owner marker", owners[0] != owners[1])
    }

    /** A failed fetch must not overwrite a good snapshot with nothing. */
    /*
     * Audit 2026-07-27. "Archive mask" promises, in its own confirmation dialog,
     * that "Mail sent here will bounce. You can restore it later", and the list
     * backs that with an Undo snackbar that flips the state back. The call
     * underneath sent JMAP `destroy` — removal, not archival — so the restore
     * path was updating an id the server had dropped. The bug survived four
     * audit passes because the one instrumented test of that path runs in DEMO
     * mode, and the demo repository archives by flipping state, which is the
     * behaviour the UI promises and NOT the behaviour the account received.
     *
     * These two tests pin the verbs apart at the seam where they diverged.
     */

    @Test
    fun `archive flips state to deleted and never destroys`() = runTest {
        every { tokenStorage.getToken() } returns "tok"
        val update = slot<MaskedEmailUpdate>()
        coEvery { jmapApi.updateMaskedEmail("tok", "m1", capture(update)) } returns Result.success(Unit)

        assertTrue(repo.archiveMaskedEmail("m1").isSuccess)

        assertEquals(MaskedEmailState.DELETED, update.captured.state)
        coVerify(exactly = 0) { jmapApi.deleteMaskedEmail(any(), any()) }
    }

    @Test
    fun `destroy is the only path that reaches jmap destroy`() = runTest {
        every { tokenStorage.getToken() } returns "tok"
        coEvery { jmapApi.deleteMaskedEmail("tok", "m1") } returns Result.success(Unit)

        assertTrue(repo.destroyMaskedEmail("m1").isSuccess)

        coVerify(exactly = 1) { jmapApi.deleteMaskedEmail("tok", "m1") }
        coVerify(exactly = 0) { jmapApi.updateMaskedEmail(any(), any(), any()) }
    }

    @Test
    fun `a failed fetch leaves the cache untouched`() = runTest {
        every { tokenStorage.getToken() } returns "token"
        coEvery { jmapApi.getMaskedEmails("token") } returns
            Result.failure(java.io.IOException("offline"))

        repo.getMaskedEmails()

        verify(exactly = 0) { cache.write(any(), any()) }
    }
}
