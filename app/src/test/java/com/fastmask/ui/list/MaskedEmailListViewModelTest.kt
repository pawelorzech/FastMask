@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)

package com.fastmask.ui.list

import com.fastmask.R
import com.fastmask.domain.model.Session
import com.fastmask.testutil.FakeSessionRepository
import com.fastmask.domain.model.CachedMasks
import com.fastmask.domain.model.CreateMaskedEmailParams
import com.fastmask.domain.model.EmailState
import com.fastmask.domain.model.UpdateMaskedEmailParams
import com.fastmask.testutil.FakeMaskedEmailRepository
import com.fastmask.testutil.MainDispatcherRule
import com.fastmask.testutil.mask
import java.io.IOException
import java.time.Instant
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class MaskedEmailListViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val session = FakeSessionRepository(initial = Session.ACCOUNT)

    private fun vm(repo: FakeMaskedEmailRepository) =
        MaskedEmailListViewModel(repo, session)

    private val t1: Instant = Instant.parse("2026-01-01T10:00:00Z")
    private val t2: Instant = Instant.parse("2026-02-01T10:00:00Z")
    private val t3: Instant = Instant.parse("2026-03-01T10:00:00Z")

    // --- sorting -----------------------------------------------------------

    @Test
    fun `list sorts by lastMessageAt desc with createdAt fallback`() = runTest {
        val repo = FakeMaskedEmailRepository(
            emails = listOf(
                mask("oldMail", createdAt = t1, lastMessageAt = t1),
                mask("noMailButNewer", createdAt = t2, lastMessageAt = null),
                mask("newestMail", createdAt = t1, lastMessageAt = t3),
            )
        )
        val viewModel = vm(repo)
        advanceUntilIdle()

        val ids = viewModel.uiState.value.emails.map { it.id }
        assertEquals(listOf("newestMail", "noMailButNewer", "oldMail"), ids)
    }

    // --- filtering (regression: Active chip count vs filtered list) --------

    @Test
    fun `active filter includes pending masks matching the chip count`() = runTest {
        val repo = FakeMaskedEmailRepository(
            emails = listOf(
                mask("enabled", state = EmailState.ENABLED),
                mask("pending", state = EmailState.PENDING),
                mask("off", state = EmailState.DISABLED),
                mask("archived", state = EmailState.DELETED),
            )
        )
        val viewModel = vm(repo)
        advanceUntilIdle()

        viewModel.onFilterChange(EmailFilter.ENABLED)

        val ids = viewModel.uiState.value.filteredEmails.map { it.id }.toSet()
        // Previously PENDING was excluded here while the chip counted it.
        assertEquals(setOf("enabled", "pending"), ids)
    }

    @Test
    fun `disabled and deleted filters match exactly one state`() = runTest {
        val repo = FakeMaskedEmailRepository(
            emails = listOf(
                mask("e", state = EmailState.ENABLED),
                mask("d", state = EmailState.DISABLED),
                mask("x", state = EmailState.DELETED),
            )
        )
        val viewModel = vm(repo)
        advanceUntilIdle()

        viewModel.onFilterChange(EmailFilter.DISABLED)
        assertEquals(listOf("d"), viewModel.uiState.value.filteredEmails.map { it.id })

        viewModel.onFilterChange(EmailFilter.DELETED)
        assertEquals(listOf("x"), viewModel.uiState.value.filteredEmails.map { it.id })
    }

    // --- search ------------------------------------------------------------

    @Test
    fun `search matches email description and domain case-insensitively`() = runTest {
        val repo = FakeMaskedEmailRepository(
            emails = listOf(
                mask("m1", description = "GitHub notifications"),
                mask("m2", forDomain = "shop.example.com"),
                mask("m3"),
            )
        )
        val viewModel = vm(repo)
        advanceUntilIdle()

        viewModel.onSearchQueryChange("github")
        assertEquals(listOf("m1"), viewModel.uiState.value.filteredEmails.map { it.id })

        viewModel.onSearchQueryChange("SHOP.EX")
        assertEquals(listOf("m2"), viewModel.uiState.value.filteredEmails.map { it.id })

        viewModel.onSearchQueryChange("m3@")
        assertEquals(listOf("m3"), viewModel.uiState.value.filteredEmails.map { it.id })
    }

    @Test
    fun `search matches URL case-insensitively and respects state filter`() = runTest {
        val repo = FakeMaskedEmailRepository(emails = listOf(
            mask("active", state = EmailState.ENABLED, url = "https://example.com/UniquePath"),
            mask("off", state = EmailState.DISABLED, url = "https://example.com/UniquePath"),
            mask("unrelated", state = EmailState.ENABLED),
        ))
        val viewModel = vm(repo)
        advanceUntilIdle()

        viewModel.onSearchQueryChange("uniquepath")
        assertEquals(setOf("active", "off"), viewModel.uiState.value.filteredEmails.map { it.id }.toSet())
        viewModel.onFilterChange(EmailFilter.ENABLED)
        assertEquals(listOf("active"), viewModel.uiState.value.filteredEmails.map { it.id })
    }

    @Test
    fun `oversized search paste is bounded before filtering`() = runTest {
        val viewModel = vm(FakeMaskedEmailRepository(emails = listOf(mask("m1"))))
        advanceUntilIdle()

        viewModel.onSearchQueryChange("x".repeat(500_000))

        assertEquals(
            MaskedEmailListViewModel.MAX_SEARCH_QUERY_LENGTH,
            viewModel.uiState.value.searchQuery.length,
        )
    }

    @Test
    fun `filtering preserves the server list activity order`() = runTest {
        val viewModel = vm(
            FakeMaskedEmailRepository(
                emails = listOf(
                    mask("old", description = "match", createdAt = t1),
                    mask("new", description = "match", createdAt = t3),
                    mask("middle", description = "other", createdAt = t2),
                ),
            ),
        )
        advanceUntilIdle()

        viewModel.onSearchQueryChange("match")

        assertEquals(listOf("new", "old"), viewModel.uiState.value.filteredEmails.map { it.id })
    }

    // --- duplicate fetch guard (regression: double fetch on first entry) ---

    @Test
    fun `refresh during in-flight initial load is queued, not run concurrently`() = runTest {
        val repo = FakeMaskedEmailRepository(emails = listOf(mask("m1")))
        val viewModel = vm(repo)
        // Init load dispatched but not yet run — mirrors the on-resume refresh
        // firing ~250ms after first composition. It no longer starts a second,
        // concurrent request, but it is not discarded either: the init fetch may
        // predate a mask that was just created, so one re-run follows.
        viewModel.refreshMaskedEmails()
        advanceUntilIdle()

        assertEquals(2, repo.getCalls)
    }

    @Test
    fun `two same-frame refreshes coalesce into a single queued re-run`() = runTest {
        val repo = FakeMaskedEmailRepository(emails = listOf(mask("m1")))
        val viewModel = vm(repo)
        advanceUntilIdle() // init load → getCalls == 1

        // Both calls land before either coroutine dispatches. The synchronous
        // in-flight guard stops the second from starting a concurrent fetch and
        // remembers it: init + the first + ONE queued re-run (not two).
        viewModel.refreshMaskedEmails()
        viewModel.refreshMaskedEmails()
        advanceUntilIdle()

        assertEquals(3, repo.getCalls)
    }

    @Test
    fun `refresh after load completes does fetch again`() = runTest {
        val repo = FakeMaskedEmailRepository(emails = listOf(mask("m1")))
        val viewModel = vm(repo)
        advanceUntilIdle()

        viewModel.refreshMaskedEmails()
        advanceUntilIdle()

        assertEquals(2, repo.getCalls)
    }

    // --- change signal and resume throttle -------------------------------

    @Test
    fun `a mutation made right after a refresh still refreshes the list once`() = runTest {
        val repo = FakeMaskedEmailRepository(emails = listOf(mask("m1")))
        val viewModel = vm(repo)
        advanceUntilIdle() // init load; the clock never moves, so the resume throttle would block everything
        assertEquals(1, repo.getCalls)

        repo.emails = listOf(mask("m1"), mask("m2"))
        repo.createMaskedEmail(CreateMaskedEmailParams())
        advanceUntilIdle()

        assertEquals(2, repo.getCalls)
        assertEquals(listOf("m1", "m2").toSet(), viewModel.uiState.value.emails.map { it.id }.toSet())
        assertEquals(false, viewModel.uiState.value.isLoading)
    }

    @Test
    fun `update archive and destroy each trigger a refresh and a failed mutation does not`() = runTest {
        val repo = FakeMaskedEmailRepository(emails = listOf(mask("m1")))
        vm(repo)
        advanceUntilIdle()

        repo.updateMaskedEmail("m1", UpdateMaskedEmailParams(state = EmailState.DISABLED))
        advanceUntilIdle()
        repo.archiveMaskedEmail("m1")
        advanceUntilIdle()
        repo.destroyMaskedEmail("m1")
        advanceUntilIdle()
        assertEquals(4, repo.getCalls)

        repo.failure = IOException("offline")
        repo.archiveMaskedEmail("m1")
        advanceUntilIdle()
        assertEquals(4, repo.getCalls)
    }

    @Test
    fun `a cold start fetches once even though the screen resumes right after`() = runTest {
        val repo = FakeMaskedEmailRepository(emails = listOf(mask("m1")))
        val viewModel = vm(repo)
        viewModel.refreshIfStale() // the first resume, before the init fetch has even run
        advanceUntilIdle()
        viewModel.refreshIfStale()
        advanceUntilIdle()

        assertEquals(1, repo.getCalls)
    }

    @Test
    fun `resume refreshes again once the last fetch is older than the throttle`() = runTest {
        val repo = FakeMaskedEmailRepository(emails = listOf(mask("m1")))
        val viewModel = vm(repo)
        advanceUntilIdle() // the init fetch stamped the real clock
        var now = System.currentTimeMillis()
        viewModel.clockMillis = { now }

        now += MaskedEmailListViewModel.STALE_AFTER_MS - 1_000
        viewModel.refreshIfStale()
        advanceUntilIdle()
        assertEquals(1, repo.getCalls)

        now += 2_000
        viewModel.refreshIfStale()
        advanceUntilIdle()
        assertEquals(2, repo.getCalls)
    }

    @Test
    fun `resume retries straight away after a failed fetch`() = runTest {
        val repo = FakeMaskedEmailRepository(failure = IOException("offline"))
        val viewModel = vm(repo)
        advanceUntilIdle()
        assertEquals(1, repo.getCalls)

        viewModel.refreshIfStale()
        advanceUntilIdle()

        assertEquals(2, repo.getCalls)
    }

    // --- error mapping -----------------------------------------------------

    @Test
    fun `network failure maps to localized network error resource`() = runTest {
        val repo = FakeMaskedEmailRepository(failure = IOException("Unable to resolve host"))
        val viewModel = vm(repo)
        advanceUntilIdle()

        assertEquals(R.string.error_network, viewModel.uiState.value.error?.message)
    }

    @Test
    fun `restoreMask re-enables an archived mask and reloads`() = runTest {
        val repo = FakeMaskedEmailRepository(
            emails = listOf(mask("m1", state = EmailState.DELETED))
        )
        val viewModel = vm(repo)
        advanceUntilIdle()

        viewModel.restoreMask("m1", restoreTo = EmailState.ENABLED)
        advanceUntilIdle()

        assertEquals(1, repo.updateCalls)
        assertEquals("m1", repo.lastUpdateId)
        assertEquals(EmailState.ENABLED, repo.lastUpdateParams?.state)
        // reload fired after the successful restore
        assertEquals(2, repo.getCalls)
    }

    @Test
    fun `restoreMask restores the pre-archive state not blindly ENABLED`() = runTest {
        val repo = FakeMaskedEmailRepository(
            emails = listOf(mask("m1", state = EmailState.DELETED))
        )
        val viewModel = vm(repo)
        advanceUntilIdle()

        // The mask was DISABLED before archiving — undo must not re-enable it.
        viewModel.restoreMask("m1", restoreTo = EmailState.DISABLED)
        advanceUntilIdle()

        assertEquals(EmailState.DISABLED, repo.lastUpdateParams?.state)
    }

    @Test
    fun `restoreMask failure surfaces an error instead of silently keeping the mask archived`() = runTest {
        val repo = FakeMaskedEmailRepository(
            emails = listOf(mask("m1", state = EmailState.DELETED))
        )
        val viewModel = vm(repo)
        advanceUntilIdle()

        repo.failure = IOException("offline now")
        viewModel.restoreMask("m1", restoreTo = EmailState.ENABLED)
        advanceUntilIdle()

        // Undo appearing to work while the mask stays archived is a silent
        // data-state lie — the failure must reach the UI.
        assertEquals(R.string.error_network, viewModel.uiState.value.error?.message)
    }

    @Test
    fun `soft refresh with cached data suppresses error`() = runTest {
        val repo = FakeMaskedEmailRepository(emails = listOf(mask("m1")))
        val viewModel = vm(repo)
        advanceUntilIdle()

        repo.failure = IOException("offline now")
        viewModel.refreshMaskedEmails()
        advanceUntilIdle()

        // Old data stays, no error banner over a working list.
        assertEquals(null, viewModel.uiState.value.error?.message)
        assertEquals(listOf("m1"), viewModel.uiState.value.emails.map { it.id })
    }

    // The two entry points used to guard on separate flags. A silent refresh
    // does not raise isLoading while the list has data, so a pull-to-refresh
    // arriving mid-refresh passed its own guard and ran a second concurrent
    // fetch whose result raced the first. The second request is now remembered and
    // runs after the first ends (init + silent + one queued re-run = 3 calls), still
    // never two at once — see the in-flight test below.
    @Test
    fun `pull-to-refresh during a silent refresh queues one re-run instead of a concurrent fetch`() = runTest {
        val repo = FakeMaskedEmailRepository(emails = listOf(mask("m1")))
        val viewModel = vm(repo)
        advanceUntilIdle() // init load → getCalls == 1

        viewModel.refreshMaskedEmails() // silent, keeps isLoading false
        viewModel.loadMaskedEmails()    // user pull-to-refresh in the same frame
        advanceUntilIdle()

        assertEquals(3, repo.getCalls)
    }

    @Test
    fun `a silent refresh during a pull-to-refresh queues one re-run instead of a concurrent fetch`() = runTest {
        val repo = FakeMaskedEmailRepository(emails = listOf(mask("m1")))
        val viewModel = vm(repo)
        advanceUntilIdle()

        viewModel.loadMaskedEmails()
        viewModel.refreshMaskedEmails()
        advanceUntilIdle()

        assertEquals(3, repo.getCalls)
    }

    // Device audit: a mask created while the initial / resume fetch was in flight
    // never appeared, because the refresh that should have picked it up was
    // dropped on the floor. It is now remembered and runs once the fetch ends.
    @Test
    fun `a refresh requested while a fetch is in flight runs once after it`() = runTest {
        val gate = kotlinx.coroutines.CompletableDeferred<Unit>()
        val repo = FakeMaskedEmailRepository(emails = listOf(mask("m1")))
        repo.beforeGet = { if (repo.getCalls == 1) gate.await() }
        val viewModel = vm(repo)
        advanceUntilIdle() // init fetch is parked on the gate
        assertEquals(1, repo.getCalls)

        repo.emails = listOf(mask("m1"), mask("created"))
        viewModel.refreshMaskedEmails()
        viewModel.refreshMaskedEmails() // coalesced: still just one re-run
        advanceUntilIdle()
        assertEquals("never two fetches at once", 1, repo.getCalls)

        gate.complete(Unit)
        advanceUntilIdle()

        assertEquals(2, repo.getCalls)
        assertEquals(listOf("m1", "created").toSet(), viewModel.uiState.value.emails.map { it.id }.toSet())
    }

    // A user-initiated load must still report failure even with data on
    // screen — unlike the silent refresh, the user is waiting on this one.
    @Test
    fun `pull-to-refresh surfaces an error even with cached data`() = runTest {
        val repo = FakeMaskedEmailRepository(emails = listOf(mask("m1")))
        val viewModel = vm(repo)
        advanceUntilIdle()

        repo.failure = IOException("offline now")
        viewModel.loadMaskedEmails()
        advanceUntilIdle()

        assertEquals(R.string.error_network, viewModel.uiState.value.error?.message)
        assertEquals(R.string.error_network, viewModel.uiState.value.inlineError?.message)
        assertEquals(listOf("m1"), viewModel.uiState.value.emails.map { it.id })
    }

    @Test
    fun `successful background refresh clears an earlier visible refresh error`() = runTest {
        val repo = FakeMaskedEmailRepository(emails = listOf(mask("m1")))
        val viewModel = vm(repo)
        advanceUntilIdle()

        repo.failure = IOException("offline now")
        viewModel.loadMaskedEmails()
        advanceUntilIdle()
        assertEquals(R.string.error_network, viewModel.uiState.value.inlineError?.message)

        repo.failure = null
        repo.emails = listOf(mask("fresh"))
        viewModel.refreshMaskedEmails()
        advanceUntilIdle()

        assertEquals(null, viewModel.uiState.value.inlineError?.message)
        assertEquals(listOf("fresh"), viewModel.uiState.value.emails.map { it.id })
    }

    // --- offline cache (B1) ------------------------------------------------

    private val cachedAt: Instant = Instant.parse("2026-07-24T09:00:00Z")

    /**
     * The commonest reason to open this app is to read back an address you
     * already created. That must not require a connection.
     */
    @Test
    fun `an offline first load falls back to the cached snapshot`() = runTest {
        val repo = FakeMaskedEmailRepository(
            failure = IOException("offline"),
            cached = CachedMasks(listOf(mask("cached1"), mask("cached2")), cachedAt),
        )
        val viewModel = vm(repo)
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(listOf("cached1", "cached2"), state.emails.map { it.id }.sorted())
        // Marked stale rather than passed off as current.
        assertEquals(cachedAt, state.cachedAt)
        // No error banner over a usable list.
        assertEquals(null, state.error)
    }

    @Test
    fun `an offline load with no cache still reports the network error`() = runTest {
        val repo = FakeMaskedEmailRepository(failure = IOException("offline"), cached = null)
        val viewModel = vm(repo)
        advanceUntilIdle()

        assertEquals(R.string.error_network, viewModel.uiState.value.error?.message)
        assertEquals(null, viewModel.uiState.value.inlineError?.message)
        assertEquals(null, viewModel.uiState.value.cachedAt)
    }

    @Test
    fun `an empty cache is not treated as a usable snapshot`() = runTest {
        val repo = FakeMaskedEmailRepository(
            failure = IOException("offline"),
            cached = CachedMasks(emptyList(), cachedAt),
        )
        val viewModel = vm(repo)
        advanceUntilIdle()

        assertEquals(R.string.error_network, viewModel.uiState.value.error?.message)
        assertEquals(null, viewModel.uiState.value.cachedAt)
    }

    /** A later successful fetch must drop the stale marker. */
    @Test
    fun `a successful fetch clears the cached marker`() = runTest {
        val repo = FakeMaskedEmailRepository(
            failure = IOException("offline"),
            cached = CachedMasks(listOf(mask("cached1")), cachedAt),
        )
        val viewModel = vm(repo)
        advanceUntilIdle()
        assertEquals(cachedAt, viewModel.uiState.value.cachedAt)

        repo.failure = null
        repo.emails = listOf(mask("fresh1"))
        viewModel.loadMaskedEmails()
        advanceUntilIdle()

        assertEquals(listOf("fresh1"), viewModel.uiState.value.emails.map { it.id })
        assertEquals(null, viewModel.uiState.value.cachedAt)
    }

    /** With masks already on screen a background failure changes nothing. */
    @Test
    fun `a silent refresh failure does not swap good data for the cache`() = runTest {
        val repo = FakeMaskedEmailRepository(
            emails = listOf(mask("live1")),
            cached = CachedMasks(listOf(mask("cached1")), cachedAt),
        )
        val viewModel = vm(repo)
        advanceUntilIdle()

        repo.failure = IOException("offline now")
        viewModel.refreshMaskedEmails()
        advanceUntilIdle()

        assertEquals(listOf("live1"), viewModel.uiState.value.emails.map { it.id })
        assertEquals(null, viewModel.uiState.value.cachedAt)
    }
}
