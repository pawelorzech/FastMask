@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)

package com.fastmask.ui.hygiene

import com.fastmask.R
import com.fastmask.domain.analytics.MonetizationEvent
import com.fastmask.domain.hygiene.HygieneBaseline
import com.fastmask.domain.hygiene.HygieneIssue
import com.fastmask.domain.model.CachedMasks
import com.fastmask.domain.model.CreateMaskedEmailParams
import com.fastmask.domain.model.EmailState
import com.fastmask.domain.model.MaskedEmail
import com.fastmask.domain.model.ProStatus
import com.fastmask.domain.model.UpdateMaskedEmailParams
import com.fastmask.domain.repository.MaskedEmailRepository
import com.fastmask.domain.usecase.DeleteMaskedEmailUseCase
import com.fastmask.domain.usecase.GetHygieneBaselineUseCase
import com.fastmask.domain.usecase.GetMaskedEmailsUseCase
import com.fastmask.domain.usecase.SaveHygieneBaselineUseCase
import com.fastmask.domain.usecase.UpdateMaskedEmailUseCase
import com.fastmask.testutil.FakeMonetizationAnalytics
import com.fastmask.testutil.FakeProRepository
import com.fastmask.testutil.MainDispatcherRule
import com.fastmask.testutil.mask
import java.io.IOException
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneOffset
import kotlin.coroutines.CoroutineContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.yield
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/** Where `viewModelScope` parks itself on lifecycle 2.8.x. */
private const val VIEW_MODEL_SCOPE_KEY =
    "androidx.lifecycle.viewmodel.internal.ViewModelCoroutineScope.JOB_KEY"

/**
 * The hygiene screen is the only place in FastMask that does something to many
 * masks at once, so the interesting behaviour is not the happy path — it is
 * what happens when half the run fails, when the user double-taps, and when
 * the entitlement is gone.
 *
 * Four rules the tests exist to hold down:
 *  - a partially failed run is never presented as a success;
 *  - "new activity" is diffed against the review's OWN baseline, never the mask
 *    cache — `MaskedEmailRepositoryImpl.getMaskedEmails()` writes through to
 *    that cache on every success and the list refreshes on every RESUME, so a
 *    diff against it is empty by the time the user gets here;
 *  - the entitlement is watched, not sampled once, so a purchase made after the
 *    paywall bounce fills the screen the customer just paid for;
 *  - a run that has started finishes and reports, whatever the screen does.
 */
class MaskHygieneViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val now: Instant = Instant.parse("2026-07-25T12:00:00Z")
    private val clock: Clock = Clock.fixed(now, ZoneOffset.UTC)

    private fun ago(days: Long): Instant = now.minus(Duration.ofDays(days))

    private val proRepository = FakeProRepository(ProStatus.PRO)
    private val analytics = FakeMonetizationAnalytics()

    // --- fixtures ----------------------------------------------------------

    private val dead1 = mask("dead1", description = "Shop A", createdAt = ago(60), lastMessageAt = null)
    private val dead2 = mask("dead2", description = "Shop B", createdAt = ago(90), lastMessageAt = null)
    private val quiet = mask("quiet", description = "Forum", createdAt = ago(700), lastMessageAt = ago(400))
    private val fine = mask(
        "fine",
        description = "Bank",
        forDomain = "bank.example",
        createdAt = ago(400),
        lastMessageAt = ago(1),
    )

    private fun repo(vararg masks: MaskedEmail) = HygieneRepository(emails = masks.toList())

    private fun TestScope.viewModel(
        repository: HygieneRepository,
        computeDispatcher: CoroutineDispatcher = StandardTestDispatcher(testScheduler),
        externalScope: CoroutineScope = CoroutineScope(StandardTestDispatcher(testScheduler)),
    ) = MaskHygieneViewModel(
        getMaskedEmailsUseCase = GetMaskedEmailsUseCase(repository),
        getHygieneBaselineUseCase = GetHygieneBaselineUseCase(repository),
        saveHygieneBaselineUseCase = SaveHygieneBaselineUseCase(repository),
        updateMaskedEmailUseCase = UpdateMaskedEmailUseCase(repository),
        deleteMaskedEmailUseCase = DeleteMaskedEmailUseCase(repository),
        proRepository = proRepository,
        analytics = analytics,
        clock = clock,
        computeDispatcher = computeDispatcher,
        externalScope = externalScope,
    )

    /**
     * Cancels the ViewModel's own scope the way the framework does when the
     * destination is popped, so a test can prove what survives it.
     *
     * The key is `viewModelScope`'s internal storage key. It is asserted rather
     * than assumed: if a lifecycle upgrade renames it this must fail loudly,
     * not quietly stop cancelling anything and pass for the wrong reason.
     */
    private fun MaskHygieneViewModel.cancelViewModelScope() {
        val scope: AutoCloseable? = getCloseable(VIEW_MODEL_SCOPE_KEY)
        assertNotNull(
            "viewModelScope is no longer stored under $VIEW_MODEL_SCOPE_KEY — " +
                "this test cancels nothing and proves nothing",
            scope,
        )
        scope!!.close()
    }

    // --- loading -----------------------------------------------------------

    /**
     * The baseline is read before the fetch and rewritten after it, so the
     * report the user is looking at is exactly what the next visit diffs
     * against. Reading it after the fetch would compare the fresh list with
     * itself and empty the "new activity" category forever.
     */
    @Test
    fun `the baseline is read before the fetch and rewritten after it`() = runTest {
        val repository = repo(fine)
        repository.baseline = HygieneBaseline.of(listOf(fine), ago(2))

        viewModel(repository)
        advanceUntilIdle()

        assertEquals(listOf("baseline", "fetch", "saveBaseline"), repository.callLog)
    }

    /**
     * The regression this whole retention exists for.
     *
     * `MaskedEmailRepositoryImpl.getMaskedEmails()` write-throughs the mask
     * cache on every success, and the list screen refreshes on every RESUME —
     * so by the time the user taps the broom icon the cache already equals the
     * server state. Diffing against it would report nothing, ever. The review
     * keeps its own baseline, which no list refresh touches.
     */
    @Test
    fun `new activity is diffed against the review baseline not the mask cache`() = runTest {
        val busyNow = mask("busy", description = "Bank", createdAt = ago(400), lastMessageAt = ago(1))
        val repository = repo(busyNow)
        // What a list refresh left behind seconds ago: already current.
        repository.cached = CachedMasks(listOf(busyNow), ago(0))
        // What the user was shown the last time they opened this screen.
        repository.baseline = HygieneBaseline.of(
            listOf(mask("busy", description = "Bank", createdAt = ago(400), lastMessageAt = ago(30))),
            reviewedAt = ago(30),
        )

        val vm = viewModel(repository)
        advanceUntilIdle()

        assertEquals(
            listOf("busy"),
            vm.uiState.value.report.masksFor(HygieneIssue.NEW_ACTIVITY).map { it.id },
        )
        // The mask cache was never even consulted.
        assertFalse("cache" in repository.callLog)
    }

    /**
     * Within one visit the user keeps seeing the same "what changed" list. The
     * baseline written on entry must not become the baseline the next refresh
     * diffs against, or pulling to refresh would erase the category the user
     * came here to read.
     */
    @Test
    fun `a refresh within the visit still diffs against the baseline it opened with`() = runTest {
        val busyNow = mask("busy", description = "Bank", createdAt = ago(400), lastMessageAt = ago(1))
        val repository = repo(busyNow)
        repository.baseline = HygieneBaseline.of(
            listOf(mask("busy", description = "Bank", createdAt = ago(400), lastMessageAt = ago(30))),
            reviewedAt = ago(30),
        )

        val vm = viewModel(repository)
        advanceUntilIdle()
        vm.refresh()
        advanceUntilIdle()

        assertEquals(
            listOf("busy"),
            vm.uiState.value.report.masksFor(HygieneIssue.NEW_ACTIVITY).map { it.id },
        )
    }

    /** The user saw nothing, so nothing may be recorded as seen. */
    @Test
    fun `a failed fetch does not overwrite the baseline`() = runTest {
        val repository = repo(fine)
        val original = HygieneBaseline.of(listOf(fine), ago(2))
        repository.baseline = original
        repository.fetchFailure = IOException("offline")

        viewModel(repository)
        advanceUntilIdle()

        assertEquals(emptyList<HygieneBaseline>(), repository.savedBaselines)
        assertEquals(original, repository.baseline)
    }

    /**
     * A mask created since the last review is new, which is not the same claim
     * as "this address started receiving mail" — asserted end to end here
     * because the baseline now travels through the repository.
     */
    @Test
    fun `a mask absent from the baseline is not new activity`() = runTest {
        val repository = repo(fine)
        repository.baseline = HygieneBaseline.of(emptyList(), ago(30))

        val vm = viewModel(repository)
        advanceUntilIdle()

        assertEquals(0, vm.uiState.value.report.count(HygieneIssue.NEW_ACTIVITY))
    }

    @Test
    fun `with pro the review is computed from the fetched masks`() = runTest {
        val repository = repo(dead1, dead2, quiet, fine)

        val vm = viewModel(repository)
        advanceUntilIdle()

        val report = vm.uiState.value.report
        assertEquals(4, report.reviewedCount)
        assertEquals(setOf("dead1", "dead2"), report.masksFor(HygieneIssue.NEVER_USED).map { it.id }.toSet())
        assertEquals(listOf("quiet"), report.masksFor(HygieneIssue.DORMANT).map { it.id })
        assertFalse(vm.uiState.value.isLoading)
        assertEquals(null, vm.uiState.value.errorRes)
    }

    /**
     * Pro gate. The review is a feature that never existed for free, so the
     * screen does no work at all without an entitlement — no fetch, no report,
     * straight to the paywall.
     */
    @Test
    fun `without pro nothing is loaded and the paywall opens`() = runTest {
        proRepository.statusFlow.value = ProStatus.FREE
        val repository = repo(dead1, dead2)

        val vm = viewModel(repository)
        val events = record(vm.events)
        advanceUntilIdle()

        assertEquals(0, repository.getCalls)
        // Not even the cache is touched — a locked screen reads nothing.
        assertEquals(emptyList<String>(), repository.callLog)
        assertTrue(vm.uiState.value.report.isClean)
        assertEquals(
            listOf(MaskHygieneEvent.OpenPro(MaskHygieneViewModel.PAYWALL_SOURCE)),
            events.received,
        )
        events.stop()
    }

    @Test
    fun `the blocked entry is tracked as a premium feature tap`() = runTest {
        proRepository.statusFlow.value = ProStatus.FREE

        viewModel(repo(dead1))
        advanceUntilIdle()

        assertEquals(
            listOf(FakeMonetizationAnalytics.Tracked(
                MonetizationEvent.PREMIUM_FEATURE_TAPPED,
                MaskHygieneViewModel.PAYWALL_SOURCE,
                null,
            )),
            analytics.tracked,
        )
    }

    /** A pending purchase is not an entitlement — same treatment as FREE. */
    @Test
    fun `a pending purchase does not unlock the review`() = runTest {
        proRepository.statusFlow.value = ProStatus.PENDING
        val repository = repo(dead1)

        val vm = viewModel(repository)
        val events = record(vm.events)
        advanceUntilIdle()

        assertEquals(0, repository.getCalls)
        assertEquals(
            listOf(MaskHygieneEvent.OpenPro(MaskHygieneViewModel.PAYWALL_SOURCE)),
            events.received,
        )
        events.stop()
    }

    /**
     * Demo mode has no persisted snapshot (`cachedMaskedEmails()` returns
     * null there by contract). The review must still render — a missing
     * baseline only empties the "new activity" category.
     */
    @Test
    fun `demo mode without a snapshot still produces a review`() = runTest {
        val repository = repo(dead1, quiet, fine)
        repository.cached = null

        val vm = viewModel(repository)
        advanceUntilIdle()

        val state = vm.uiState.value
        assertEquals(null, state.errorRes)
        assertEquals(3, state.report.reviewedCount)
        assertEquals(0, state.report.count(HygieneIssue.NEW_ACTIVITY))
    }

    @Test
    fun `a load failure surfaces a localized error and no report`() = runTest {
        val repository = repo(dead1)
        repository.fetchFailure = IOException("offline")

        val vm = viewModel(repository)
        advanceUntilIdle()

        assertEquals(R.string.error_network, vm.uiState.value.errorRes)
        assertTrue(vm.uiState.value.report.isClean)
        assertFalse(vm.uiState.value.isLoading)
    }

    /**
     * Hundreds of masks get classified on every entry. Doing that on the main
     * dispatcher is a dropped-frame bug waiting for the first power user, so
     * the work is required to leave it.
     */
    @Test
    fun `classification runs off the main dispatcher`() = runTest {
        val counting = CountingDispatcher(StandardTestDispatcher(testScheduler))
        val repository = repo(dead1, dead2, quiet, fine)

        viewModel(repository, computeDispatcher = counting)
        advanceUntilIdle()

        assertTrue("classification never left the main dispatcher", counting.dispatches > 0)
    }

    // --- selection ---------------------------------------------------------

    @Test
    fun `tapping a mask selects it and tapping again deselects it`() = runTest {
        val vm = viewModel(repo(dead1, dead2))
        advanceUntilIdle()

        vm.onMaskToggled("dead1")
        assertEquals(setOf("dead1"), vm.uiState.value.selectedIds)

        vm.onMaskToggled("dead1")
        assertEquals(emptySet<String>(), vm.uiState.value.selectedIds)
    }

    @Test
    fun `select all takes every mask in that category and leaves the rest`() = runTest {
        val vm = viewModel(repo(dead1, dead2, quiet, fine))
        advanceUntilIdle()

        vm.onSelectAll(HygieneIssue.NEVER_USED)

        assertEquals(setOf("dead1", "dead2"), vm.uiState.value.selectedIds)
    }

    @Test
    fun `clearing the selection empties it`() = runTest {
        val vm = viewModel(repo(dead1, dead2))
        advanceUntilIdle()

        vm.onSelectAll(HygieneIssue.NEVER_USED)
        vm.onClearSelection()

        assertEquals(emptySet<String>(), vm.uiState.value.selectedIds)
    }

    // --- bulk actions ------------------------------------------------------

    @Test
    fun `a bulk action with nothing selected does nothing at all`() = runTest {
        val repository = repo(dead1, dead2)
        val vm = viewModel(repository)
        val events = record(vm.events)
        advanceUntilIdle()
        val fetchesBefore = repository.getCalls

        vm.onBulkAction(BulkAction.DISABLE)
        advanceUntilIdle()

        assertEquals(emptyList<Pair<String, UpdateMaskedEmailParams>>(), repository.updates)
        assertEquals(emptyList<String>(), repository.deletes)
        assertEquals(fetchesBefore, repository.getCalls)
        assertEquals(emptyList<MaskHygieneEvent>(), events.received)
        assertFalse(vm.uiState.value.actionInFlight)
        events.stop()
    }

    @Test
    fun `disabling the selection updates each selected mask exactly once`() = runTest {
        val repository = repo(dead1, dead2, quiet, fine)
        val vm = viewModel(repository)
        advanceUntilIdle()

        vm.onSelectAll(HygieneIssue.NEVER_USED)
        vm.onBulkAction(BulkAction.DISABLE)
        advanceUntilIdle()

        assertEquals(2, repository.updates.size)
        assertEquals(setOf("dead1", "dead2"), repository.updates.map { it.first }.toSet())
        assertTrue(repository.updates.all { it.second.state == EmailState.DISABLED })
        // Bulk delete is deliberately not offered — nothing may be destroyed here.
        assertEquals(emptyList<String>(), repository.deletes)
    }

    @Test
    fun `archiving the selection archives each selected mask exactly once`() = runTest {
        val repository = repo(dead1, dead2, quiet, fine)
        val vm = viewModel(repository)
        advanceUntilIdle()

        vm.onSelectAll(HygieneIssue.NEVER_USED)
        vm.onBulkAction(BulkAction.ARCHIVE)
        advanceUntilIdle()

        assertEquals(setOf("dead1", "dead2"), repository.deletes.toSet())
        assertEquals(2, repository.deletes.size)
        assertEquals(emptyList<Pair<String, UpdateMaskedEmailParams>>(), repository.updates)
    }

    @Test
    fun `a fully successful run reports a complete success and clears the selection`() = runTest {
        val repository = repo(dead1, dead2)
        val vm = viewModel(repository)
        val events = record(vm.events)
        advanceUntilIdle()

        vm.onSelectAll(HygieneIssue.NEVER_USED)
        vm.onBulkAction(BulkAction.DISABLE)
        advanceUntilIdle()

        val result = events.received.filterIsInstance<MaskHygieneEvent.BulkActionFinished>()
            .single().result
        assertEquals(BulkAction.DISABLE, result.action)
        assertEquals(2, result.requested)
        assertEquals(emptyList<String>(), result.failedIds)
        assertTrue(result.isCompleteSuccess)
        assertFalse(result.isPartial)
        assertEquals(emptySet<String>(), vm.uiState.value.selectedIds)
        assertFalse(vm.uiState.value.actionInFlight)
        events.stop()
    }

    /**
     * JMAP gives us no batch call, so ten masks are ten requests and any of
     * them can fail on its own. Telling the user "done" after seven of ten is
     * the exact lie this screen must never tell.
     */
    @Test
    fun `a partial failure is reported honestly`() = runTest {
        val many = (1..10).map { mask("m$it", description = "Shop $it", createdAt = ago(60), lastMessageAt = null) }
        val repository = HygieneRepository(emails = many)
        repository.failIds += setOf("m3", "m7", "m9")
        val vm = viewModel(repository)
        val events = record(vm.events)
        advanceUntilIdle()

        vm.onSelectAll(HygieneIssue.NEVER_USED)
        vm.onBulkAction(BulkAction.DISABLE)
        advanceUntilIdle()

        val result = events.received.filterIsInstance<MaskHygieneEvent.BulkActionFinished>()
            .single().result
        assertEquals(10, result.requested)
        assertEquals(7, result.succeeded.size)
        assertEquals(setOf("m3", "m7", "m9"), result.failedIds.toSet())
        assertFalse("a run with three failures is not a success", result.isCompleteSuccess)
        assertTrue(result.isPartial)
        // Every mask was still attempted — a failure must not abort the run.
        assertEquals(10, repository.updates.size)
        events.stop()
    }

    /** The failures are the ones worth retrying, so they stay selected. */
    @Test
    fun `a partial failure keeps exactly the failed masks selected`() = runTest {
        val many = (1..10).map { mask("m$it", description = "Shop $it", createdAt = ago(60), lastMessageAt = null) }
        val repository = HygieneRepository(emails = many)
        repository.failIds += setOf("m3", "m7", "m9")
        val vm = viewModel(repository)
        advanceUntilIdle()

        vm.onSelectAll(HygieneIssue.NEVER_USED)
        vm.onBulkAction(BulkAction.DISABLE)
        advanceUntilIdle()

        assertEquals(setOf("m3", "m7", "m9"), vm.uiState.value.selectedIds)
    }

    /**
     * The confirm button is a normal Compose button and a bulk run takes
     * seconds; a second tap must not double every request on the account.
     */
    @Test
    fun `a double tap runs the action only once`() = runTest {
        val repository = repo(dead1, dead2)
        val vm = viewModel(repository)
        advanceUntilIdle()

        vm.onSelectAll(HygieneIssue.NEVER_USED)
        // Both taps land in the same frame, before the first coroutine runs.
        vm.onBulkAction(BulkAction.DISABLE)
        vm.onBulkAction(BulkAction.DISABLE)
        advanceUntilIdle()

        assertEquals(2, repository.updates.size)
    }

    @Test
    fun `the review reloads after a bulk action so the lists reflect it`() = runTest {
        val repository = repo(dead1, dead2)
        val vm = viewModel(repository)
        advanceUntilIdle()
        val fetchesBefore = repository.getCalls

        vm.onSelectAll(HygieneIssue.NEVER_USED)
        vm.onBulkAction(BulkAction.DISABLE)
        advanceUntilIdle()

        assertEquals(fetchesBefore + 1, repository.getCalls)
    }

    // --- undo --------------------------------------------------------------

    /**
     * Same promise the list already makes after archiving a single mask: undo
     * returns each address to the state it had, not to a blanket ENABLED.
     */
    @Test
    fun `undo restores the state each mask had before the action`() = runTest {
        val enabled = mask("enabled", state = EmailState.ENABLED, createdAt = ago(60), lastMessageAt = null)
        val pending = mask("pending", state = EmailState.PENDING, createdAt = ago(60), lastMessageAt = null)
        val disabled = mask("disabled", state = EmailState.DISABLED, createdAt = ago(60), lastMessageAt = null)
        val repository = HygieneRepository(emails = listOf(enabled, pending, disabled))
        val vm = viewModel(repository)
        val events = record(vm.events)
        advanceUntilIdle()

        vm.onSelectAll(HygieneIssue.NEVER_USED)
        vm.onBulkAction(BulkAction.ARCHIVE)
        advanceUntilIdle()

        val result = events.received.filterIsInstance<MaskHygieneEvent.BulkActionFinished>()
            .single().result
        vm.undoBulkAction(result)
        advanceUntilIdle()

        val restored = repository.updates.associate { it.first to it.second.state }
        assertEquals(
            mapOf(
                "enabled" to EmailState.ENABLED,
                "pending" to EmailState.PENDING,
                "disabled" to EmailState.DISABLED,
            ),
            restored,
        )
        events.stop()
    }

    /** Undoing something that never happened would re-enable a mask the user still wants off. */
    @Test
    fun `undo touches only the masks whose action succeeded`() = runTest {
        val repository = repo(dead1, dead2, quiet)
        repository.failIds += "dead2"
        val vm = viewModel(repository)
        val events = record(vm.events)
        advanceUntilIdle()

        vm.onSelectAll(HygieneIssue.NEVER_USED)
        vm.onBulkAction(BulkAction.ARCHIVE)
        advanceUntilIdle()

        val result = events.received.filterIsInstance<MaskHygieneEvent.BulkActionFinished>()
            .single().result
        repository.failIds.clear()
        vm.undoBulkAction(result)
        advanceUntilIdle()

        assertEquals(listOf("dead1"), repository.updates.map { it.first })
        events.stop()
    }

    // --- pro gate on the actions themselves --------------------------------

    /**
     * The entitlement can disappear between opening the screen and confirming
     * (refund, another device). The gate is re-checked at the call, exactly
     * like the settings screen does for accent and app lock.
     */
    @Test
    fun `losing pro before a bulk action blocks it and opens the paywall`() = runTest {
        val repository = repo(dead1, dead2)
        val vm = viewModel(repository)
        val events = record(vm.events)
        advanceUntilIdle()

        vm.onSelectAll(HygieneIssue.NEVER_USED)
        proRepository.statusFlow.value = ProStatus.FREE
        vm.onBulkAction(BulkAction.DISABLE)
        advanceUntilIdle()

        assertEquals(emptyList<Pair<String, UpdateMaskedEmailParams>>(), repository.updates)
        assertEquals(emptyList<String>(), repository.deletes)
        assertEquals(
            listOf(MaskHygieneEvent.OpenPro(MaskHygieneViewModel.PAYWALL_SOURCE)),
            events.received,
        )
        // The selection survives so buying Pro drops the user back on the job.
        assertEquals(setOf("dead1", "dead2"), vm.uiState.value.selectedIds)
        events.stop()
    }

    // --- the entitlement arriving while the screen is alive ----------------

    /**
     * The paid-for-it-and-it-looks-broken case.
     *
     * A free user taps the broom icon, bounces to the paywall, buys Pro and
     * pops back. The hygiene destination is still on the back stack, so the
     * SAME ViewModel is reused — and if the gate were a one-shot read it would
     * still be holding the empty report, rendering "No masks yet" to a customer
     * who has just paid, with no refresh button anywhere on the screen.
     */
    @Test
    fun `buying pro after the bounce fills the report without a new view model`() = runTest {
        proRepository.statusFlow.value = ProStatus.FREE
        val repository = repo(dead1, dead2, quiet, fine)

        val vm = viewModel(repository)
        val events = record(vm.events)
        advanceUntilIdle()
        assertTrue("the gate should have left nothing behind", vm.uiState.value.report.isClean)
        assertEquals(0, repository.getCalls)

        proRepository.statusFlow.value = ProStatus.PRO
        advanceUntilIdle()

        val report = vm.uiState.value.report
        assertEquals(4, report.reviewedCount)
        assertEquals(setOf("dead1", "dead2"), report.masksFor(HygieneIssue.NEVER_USED).map { it.id }.toSet())
        assertEquals(null, vm.uiState.value.errorRes)
        assertFalse(vm.uiState.value.isLoading)
        // ...and it must not bounce the paying customer to the paywall again.
        assertEquals(
            1,
            events.received.count { it is MaskHygieneEvent.OpenPro },
        )
        events.stop()
    }

    /** PENDING resolving into PRO is the same purchase, one step later. */
    @Test
    fun `a pending purchase turning into pro fills the report`() = runTest {
        proRepository.statusFlow.value = ProStatus.PENDING
        val repository = repo(dead1, dead2)

        val vm = viewModel(repository)
        advanceUntilIdle()

        proRepository.statusFlow.value = ProStatus.PRO
        advanceUntilIdle()

        assertEquals(2, vm.uiState.value.report.reviewedCount)
    }

    /** Losing the entitlement must not silently reload behind the paywall. */
    @Test
    fun `losing pro does not trigger a reload`() = runTest {
        val repository = repo(dead1, dead2)
        val vm = viewModel(repository)
        advanceUntilIdle()
        val fetchesBefore = repository.getCalls

        proRepository.statusFlow.value = ProStatus.FREE
        advanceUntilIdle()

        assertEquals(fetchesBefore, repository.getCalls)
        assertEquals(2, vm.uiState.value.report.reviewedCount)
    }

    // --- leaving mid-run ---------------------------------------------------

    /**
     * The system back gesture reaches `onClearSelection` without passing the
     * Clear button's own `enabled` guard. Emptying the selection mid-run would
     * hide the bottom bar and claim nothing is selected while masks 5..10 are
     * still being changed on the account.
     */
    @Test
    fun `the selection cannot be cleared while a run is in flight`() = runTest {
        val repository = repo(dead1, dead2)
        val vm = viewModel(repository)
        advanceUntilIdle()

        vm.onSelectAll(HygieneIssue.NEVER_USED)
        vm.onBulkAction(BulkAction.DISABLE)
        assertTrue("the run should be in flight", vm.uiState.value.actionInFlight)

        vm.onClearSelection()

        assertEquals(setOf("dead1", "dead2"), vm.uiState.value.selectedIds)
        advanceUntilIdle()
        // ...and it is clearable again the moment the run is over.
        vm.onClearSelection()
        assertEquals(emptySet<String>(), vm.uiState.value.selectedIds)
    }

    /**
     * A sequential run is N separate account mutations. If it died with the
     * screen, the user would be left with an unknown number of masks archived,
     * no count and no undo — a partial mutation presented as nothing at all.
     * So the run outlives the ViewModel and still reports what it did.
     */
    @Test
    fun `a run interrupted by the screen going away still finishes and reports`() = runTest {
        val many = (1..10).map { mask("m$it", description = "Shop $it", createdAt = ago(60), lastMessageAt = null) }
        val repository = HygieneRepository(emails = many)
        val vm = viewModel(repository)
        val events = record(vm.events)
        advanceUntilIdle()

        // Mask 4 of 10 is in flight when the destination is popped.
        repository.beforeUpdate = { index -> if (index == 4) vm.cancelViewModelScope() }

        vm.onSelectAll(HygieneIssue.NEVER_USED)
        vm.onBulkAction(BulkAction.DISABLE)
        advanceUntilIdle()

        assertEquals("every selected mask must still be attempted", 10, repository.updates.size)
        val result = events.received.filterIsInstance<MaskHygieneEvent.BulkActionFinished>()
            .single().result
        assertEquals(10, result.succeeded.size)
        assertEquals(emptyList<String>(), result.failedIds)
        events.stop()
    }

    // --- a reload that fails on top of a report ----------------------------

    /**
     * Connectivity drops mid-series: 7 of 10 land, 3 fail, and the follow-up
     * reload fails for the same reason. Blanking the screen there would throw
     * away the one thing the user needs — which three addresses still need
     * retrying — and replace a usable report with a bare error card.
     */
    @Test
    fun `a failed reload after a partial run keeps the report and the retry list`() = runTest {
        val many = (1..10).map { mask("m$it", description = "Shop $it", createdAt = ago(60), lastMessageAt = null) }
        val repository = HygieneRepository(emails = many)
        repository.failIds += setOf("m3", "m7", "m9")
        val vm = viewModel(repository)
        advanceUntilIdle()

        vm.onSelectAll(HygieneIssue.NEVER_USED)
        repository.fetchFailure = IOException("offline")
        vm.onBulkAction(BulkAction.DISABLE)
        advanceUntilIdle()

        val state = vm.uiState.value
        assertEquals(setOf("m3", "m7", "m9"), state.selectedIds)
        assertEquals(10, state.report.reviewedCount)
        assertEquals(R.string.error_network, state.errorRes)
        assertFalse(state.isLoading)
    }

    /** A refresh that fails is not a reason to take the masks off the screen. */
    @Test
    fun `a failed refresh keeps the report already on screen`() = runTest {
        val repository = repo(dead1, dead2, quiet, fine)
        val vm = viewModel(repository)
        advanceUntilIdle()

        repository.fetchFailure = IOException("offline")
        vm.refresh()
        advanceUntilIdle()

        assertEquals(4, vm.uiState.value.report.reviewedCount)
        assertEquals(R.string.error_network, vm.uiState.value.errorRes)
    }

    // --- helpers -----------------------------------------------------------

    private fun TestScope.record(flow: Flow<MaskHygieneEvent>): EventRecorder {
        // Not backgroundScope: on coroutines-test 1.7.3 it is not driven by
        // advanceUntilIdle(), so a collector started there never sees anything.
        val scope = CoroutineScope(SupervisorJob() + StandardTestDispatcher(testScheduler))
        return EventRecorder(scope, flow)
    }

    private class EventRecorder(private val scope: CoroutineScope, flow: Flow<MaskHygieneEvent>) {
        val received = mutableListOf<MaskHygieneEvent>()
        private val job: Job = scope.launch { flow.collect { received += it } }
        fun stop() {
            job.cancel()
        }
    }

    /** Proves the classification work is handed to a dispatcher, not run inline. */
    private class CountingDispatcher(private val delegate: CoroutineDispatcher) : CoroutineDispatcher() {
        var dispatches = 0
        override fun dispatch(context: CoroutineContext, block: Runnable) {
            dispatches++
            delegate.dispatch(context, block)
        }
    }

    /**
     * Per-id failures are the point: the shared [com.fastmask.testutil.FakeMaskedEmailRepository]
     * can only fail everything at once, which cannot express "7 of 10 worked".
     */
    private class HygieneRepository(
        var emails: List<MaskedEmail> = emptyList(),
        var cached: CachedMasks? = null,
        var baseline: HygieneBaseline? = null,
    ) : MaskedEmailRepository {

        var fetchFailure: Throwable? = null
        val failIds = mutableSetOf<String>()

        /** Runs before the numbered update lands, to interleave with the run. */
        var beforeUpdate: ((Int) -> Unit)? = null

        /** "baseline" / "fetch" / "saveBaseline" in call order. */
        val callLog = mutableListOf<String>()
        var getCalls = 0
        val updates = mutableListOf<Pair<String, UpdateMaskedEmailParams>>()
        val deletes = mutableListOf<String>()
        val savedBaselines = mutableListOf<HygieneBaseline>()

        override suspend fun getMaskedEmails(): Result<List<MaskedEmail>> {
            getCalls++
            callLog += "fetch"
            fetchFailure?.let { return Result.failure(it) }
            return Result.success(emails)
        }

        override suspend fun cachedMaskedEmails(): CachedMasks? {
            callLog += "cache"
            return cached
        }

        override suspend fun hygieneBaseline(): HygieneBaseline? {
            callLog += "baseline"
            return baseline
        }

        override suspend fun saveHygieneBaseline(baseline: HygieneBaseline) {
            callLog += "saveBaseline"
            savedBaselines += baseline
            this.baseline = baseline
        }

        override suspend fun createMaskedEmail(params: CreateMaskedEmailParams): Result<MaskedEmail> =
            throw UnsupportedOperationException("hygiene never creates masks")

        override suspend fun updateMaskedEmail(
            id: String,
            params: UpdateMaskedEmailParams,
        ): Result<Unit> {
            beforeUpdate?.invoke(updates.size + 1)
            // A real JMAP call suspends, which is the only reason a cancelled
            // run ever stops partway. Without a suspension point here every
            // cancellation test would pass for the wrong reason.
            yield()
            updates += id to params
            return if (id in failIds) Result.failure(IOException("update failed: $id"))
            else Result.success(Unit)
        }

        override suspend fun deleteMaskedEmail(id: String): Result<Unit> {
            deletes += id
            return if (id in failIds) Result.failure(IOException("archive failed: $id"))
            else Result.success(Unit)
        }
    }
}
