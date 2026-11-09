package com.fastmask.data.repository

import com.fastmask.data.api.JmapApi
import com.fastmask.data.api.MaskedEmailDto
import com.fastmask.data.local.MaskedEmailCache
import com.fastmask.data.local.TokenStorage
import com.fastmask.domain.model.EmailState
import com.fastmask.domain.model.MaskedEmail
import com.fastmask.domain.model.CreateMaskedEmailParams
import com.fastmask.domain.model.UpdateMaskedEmailParams
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import java.time.Instant
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.assertFalse
import org.junit.Test

/** DTO → domain mapping: dates, states, and the not-authenticated guard. */
class MaskedEmailRepositoryImplTest {

    private val jmapApi = mockk<JmapApi>()
    private val tokenStorage = mockk<TokenStorage>()
    private val cache = mockk<MaskedEmailCache>(relaxed = true)
    private val appScope = CoroutineScope(SupervisorJob() + UnconfinedTestDispatcher())
    private val repo = MaskedEmailRepositoryImpl(jmapApi, tokenStorage, cache, UnconfinedTestDispatcher(), appScope)

    // A relaxed mock would not run the fetch; the real cache does, then writes.
    init {
        coEvery { cache.refresh(any(), any(), any()) } coAnswers { thirdArg<FetchMasks>().invoke() }
    }

    private fun dto(
        createdAt: String? = null,
        lastMessageAt: String? = null,
        state: EmailState = EmailState.ENABLED,
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
    fun `missing token fails without touching the network`() = runTest {
        every { tokenStorage.getToken() } returns null

        val result = repo.getMaskedEmails()

        assertTrue(result.isFailure)
        // No stubbing for jmapApi.getMaskedEmails — a call would throw MockKException.
    }

    @Test
    fun `create normalizes website input to Fastmail forDomain origin`() = runTest {
        every { tokenStorage.getToken() } returns "tok"
        val create = slot<CreateMaskedEmailParams>()
        coEvery { jmapApi.createMaskedEmail("tok", capture(create)) } returns Result.success(dto())

        val result = repo.createMaskedEmail(
            CreateMaskedEmailParams(forDomain = "https://WWW.Example.com/signup?ref=mail"),
        )

        assertTrue(result.isSuccess)
        assertEquals("https://www.example.com", create.captured.forDomain)
    }

    @Test
    fun `invalid forDomain fails before a JMAP mutation`() = runTest {
        every { tokenStorage.getToken() } returns "tok"

        val result = repo.createMaskedEmail(CreateMaskedEmailParams(forDomain = "not a host"))

        assertTrue(result.isFailure)
        coVerify(exactly = 0) { jmapApi.createMaskedEmail(any(), any()) }
    }

    @Test
    fun `update keeps empty forDomain as an explicit clear`() = runTest {
        every { tokenStorage.getToken() } returns "tok"
        val update = slot<UpdateMaskedEmailParams>()
        coEvery { jmapApi.updateMaskedEmail("tok", "m1", capture(update)) } returns Result.success(Unit)

        val result = repo.updateMaskedEmail("m1", UpdateMaskedEmailParams(forDomain = ""))

        assertTrue(result.isSuccess)
        assertEquals("", update.captured.forDomain)
        assertFalse(update.captured.forDomain == null)
    }

    @Test
    fun `clearing url is represented as json null intent rather than an empty url`() = runTest {
        every { tokenStorage.getToken() } returns "tok"
        val update = slot<UpdateMaskedEmailParams>()
        coEvery { jmapApi.updateMaskedEmail("tok", "m1", capture(update)) } returns Result.success(Unit)

        repo.updateMaskedEmail("m1", UpdateMaskedEmailParams(url = "")).getOrThrow()

        assertNull(update.captured.urlToSet)
        assertTrue(update.captured.clearsUrl)
    }

    // --- offline cache write-through (B1) ----------------------------------

    @Test
    fun `a successful fetch is written through to the cache`() = runTest {
        every { tokenStorage.getToken() } returns "token"
        coEvery { jmapApi.getMaskedEmails("token") } returns Result.success(listOf(dto()))

        val result = repo.getMaskedEmails()

        assertEquals("m1", result.getOrThrow().single().id)
        // The owner marker must be present: a snapshot without one is read back
        // as "not mine" and the offline list silently stays empty.
        coVerify { cache.refresh(match { owner -> owner.isNotBlank() }, any(), any()) }
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
        coEvery { cache.refresh(capture(written), any(), any()) } coAnswers { thirdArg<FetchMasks>().invoke() }
        val readWith = slot<String>()
        every { cache.read(capture(readWith)) } returns null

        repo.getMaskedEmails()
        repo.cachedMaskedEmails()

        assertEquals(written.captured, readWith.captured)
    }

    @Test
    fun `a different token asks the cache for a different owner`() = runTest {
        val owners = mutableListOf<String>()
        coEvery { cache.refresh(capture(owners), any(), any()) } coAnswers { thirdArg<FetchMasks>().invoke() }
        coEvery { jmapApi.getMaskedEmails(any()) } returns Result.success(listOf(dto()))

        every { tokenStorage.getToken() } returns "account-a-token"
        repo.getMaskedEmails()
        every { tokenStorage.getToken() } returns "account-b-token"
        repo.getMaskedEmails()

        assertEquals(2, owners.size)
        assertTrue("two accounts must not share an owner marker", owners[0] != owners[1])
    }

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
        val update = slot<UpdateMaskedEmailParams>()
        coEvery { jmapApi.updateMaskedEmail("tok", "m1", capture(update)) } returns Result.success(Unit)

        assertTrue(repo.archiveMaskedEmail("m1").isSuccess)

        assertEquals(EmailState.DELETED, update.captured.state)
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
    fun `a failed fetch is returned as is`() = runTest {
        every { tokenStorage.getToken() } returns "token"
        val offline = java.io.IOException("offline")
        coEvery { jmapApi.getMaskedEmails("token") } returns Result.failure(offline)

        assertEquals(offline, repo.getMaskedEmails().exceptionOrNull())
    }

    // A sign-out during an in-flight refresh must not put the snapshot back.
    // That guard lives in the cache, which only works if the network call runs
    // inside its refresh(); the guard itself is pinned against the real cache in
    // MaskedEmailCacheTest (instrumented, it needs the Keystore).
    @Test
    fun `the network call runs inside the cache refresh`() = runTest {
        every { tokenStorage.getToken() } returns "tok"
        var insideRefresh = false
        var fetchedInside = false
        coEvery { cache.refresh(any(), any(), any()) } coAnswers {
            insideRefresh = true
            thirdArg<FetchMasks>().invoke().also { insideRefresh = false }
        }
        coEvery { jmapApi.getMaskedEmails("tok") } coAnswers {
            fetchedInside = insideRefresh
            Result.success(listOf(dto()))
        }

        repo.getMaskedEmails()

        assertTrue(fetchedInside)
    }

    // A request that may already have reached Fastmail must complete even when
    // the caller (a ViewModel that was cleared) stops waiting for it.
    @Test
    fun `a mutation completes after its caller is cancelled`() = runTest {
        every { tokenStorage.getToken() } returns "tok"
        val gate = CompletableDeferred<Unit>()
        var completed = false
        coEvery { jmapApi.updateMaskedEmail("tok", "m1", any()) } coAnswers {
            gate.await()
            completed = true
            Result.success(Unit)
        }

        val caller = launch { repo.updateMaskedEmail("m1", UpdateMaskedEmailParams(state = EmailState.ENABLED)) }
        runCurrent() // the request is now parked inside the API call
        caller.cancel()
        caller.join()
        assertFalse(completed)

        gate.complete(Unit)
        assertTrue(completed)
    }

    // The list refetches off this signal, so it must follow success and only success.
    @Test
    fun `a successful mutation announces a change and a failed one does not`() = runTest {
        every { tokenStorage.getToken() } returns "tok"
        coEvery { jmapApi.updateMaskedEmail("tok", "ok", any()) } returns Result.success(Unit)
        coEvery { jmapApi.updateMaskedEmail("tok", "bad", any()) } returns Result.failure(java.io.IOException("offline"))
        var changes = 0
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { repo.changes.collect { changes++ } }

        repo.updateMaskedEmail("bad", UpdateMaskedEmailParams(state = EmailState.DISABLED))
        assertEquals(0, changes)

        repo.updateMaskedEmail("ok", UpdateMaskedEmailParams(state = EmailState.DISABLED))
        assertEquals(1, changes)
        repo.archiveMaskedEmail("ok")
        assertEquals(2, changes)
    }
}

private typealias FetchMasks = suspend () -> Result<List<MaskedEmail>>
