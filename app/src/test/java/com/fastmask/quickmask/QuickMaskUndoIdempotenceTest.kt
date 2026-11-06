package com.fastmask.quickmask

import android.content.Context
import com.fastmask.domain.usecase.QuickMaskCreator
import com.fastmask.domain.usecase.QuickMaskResult
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import com.fastmask.testutil.MainDispatcherRule
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import io.mockk.verify
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/**
 * `createFromTile` got an in-flight guard in an earlier audit because a Quick
 * Settings tile gives no feedback until the notification arrives a round-trip
 * later, so impatient taps minted several real masks. Undo is reachable exactly
 * the same way and had no guard.
 *
 * `notifier.cancel` runs inside the coroutine on Dispatchers.IO, so the Undo
 * button is still on screen for tens of milliseconds after the first tap. Two
 * broadcasts meant two `destroy` calls for one id: the first succeeded, the
 * second came back `notFound` — reported as a failure — and the resulting
 * "Undo failed" overwrote the truthful "Undone" in the same notification slot.
 * The user was told the mask was still on their account when it was gone.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class QuickMaskUndoIdempotenceTest {

    private val creator = mockk<QuickMaskCreator>()
    private val notifier = mockk<QuickMaskNotifier>(relaxed = true)

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private fun runner() = QuickMaskRunner(
        context = mockk<Context>(relaxed = true),
        quickMaskCreator = creator,
        notifier = notifier,
        // The runner's own scope, on the test scheduler: with Dispatchers.IO the
        // assertions raced a real thread pool.
        dispatcher = mainDispatcherRule.dispatcher,
    )

    @Test
    fun `a double-tapped undo destroys the mask once`() = runTest {
        coEvery { creator.undo("m1") } returns true
        val runner = runner()

        runner.undo("m1")
        runner.undo("m1")
        advanceUntilIdle()

        coVerify(exactly = 1) { creator.undo("m1") }
    }

    // The lie this guard exists to prevent: a second result overwriting the
    // first in the shared notification slot.
    @Test
    fun `a double-tapped undo reports its result once`() = runTest {
        coEvery { creator.undo("m1") } returns true
        val runner = runner()

        runner.undo("m1")
        runner.undo("m1")
        advanceUntilIdle()

        verify(exactly = 1) { notifier.showUndoResult(success = true) }
        verify(exactly = 0) { notifier.showUndoResult(success = false) }
    }

    // Two different masks undone in sequence are both legitimate, so the guard
    // has to be per-id rather than a single flag.
    @Test
    fun `undoing two different masks is not blocked`() = runTest {
        coEvery { creator.undo(any()) } returns true
        val runner = runner()

        runner.undo("m1")
        runner.undo("m2")
        advanceUntilIdle()

        coVerify(exactly = 1) { creator.undo("m1") }
        coVerify(exactly = 1) { creator.undo("m2") }
    }

    // A guard that latched on failure would make itself the reason a later
    // attempt at the same mask was refused.
    @Test
    fun `a failed undo releases the id so the mask can be undone later`() = runTest {
        coEvery { creator.undo("m1") } returns false
        val runner = runner()

        runner.undo("m1")
        advanceUntilIdle()

        coEvery { creator.undo("m1") } returns true
        runner.undo("m1")
        advanceUntilIdle()

        coVerify(exactly = 2) { creator.undo("m1") }
        verify(exactly = 1) { notifier.showUndoResult(success = true) }
    }

    @Test
    fun `the second tap still dismisses the notification it came from`() = runTest {
        coEvery { creator.undo("m1") } returns true
        val runner = runner()

        runner.undo("m1")
        advanceUntilIdle()
        runner.undo("m1")
        advanceUntilIdle()

        verify(exactly = 2) { notifier.cancel(QuickMaskSlot.CREATED) }
    }

    // The context has no ClipboardManager, so the copy genuinely fails: the
    // confirmation must not claim the address is on the clipboard.
    @Test
    fun `a failed clipboard copy is reported as not copied`() = runTest {
        coEvery { creator.create() } returns QuickMaskResult.Created(id = "m1", email = "a@b.c")
        val runner = runner()

        runner.createFromTile {}
        advanceUntilIdle()

        verify(exactly = 1) { notifier.showCreated(id = "m1", copied = false) }
    }

    @Test
    fun `the undo timeout leaves headroom inside the goAsync window`() {
        assertTrue(QuickMaskUndoReceiver.UNDO_TIMEOUT_MS < QuickMaskUndoReceiver.GO_ASYNC_BUDGET_MS)
    }

    @Test
    fun `a cancelled create releases the in-flight guard`() = runTest {
        coEvery { creator.create() } throws CancellationException("stopped")
        val runner = runner()

        runner.createFromTile {}
        advanceUntilIdle()

        verify(exactly = 0) { notifier.showFailure(any()) }
        coEvery { creator.create() } returns QuickMaskResult.NotSignedIn
        runner.createFromTile {}
        advanceUntilIdle()

        coVerify(exactly = 2) { creator.create() }
    }

    @Test
    fun `a cancelled undo releases the id for retry`() = runTest {
        coEvery { creator.undo("m1") } throws CancellationException("stopped")
        val runner = runner()

        runner.undo("m1")
        advanceUntilIdle()

        verify(exactly = 0) { notifier.showUndoResult(success = false) }
        coEvery { creator.undo("m1") } returns true
        runner.undo("m1")
        advanceUntilIdle()

        coVerify(exactly = 2) { creator.undo("m1") }
        verify(exactly = 1) { notifier.showUndoResult(success = true) }
    }

    @Test
    fun `a hanging undo times out before BroadcastReceiver ANR deadline and releases latch`() = runTest {
        coEvery { creator.undo("m1") } coAnswers {
            delay(30_000L)
            true
        }
        val runner = runner()
        var finished = false

        runner.undo("m1").invokeOnCompletion { finished = true }
        advanceTimeBy(QuickMaskUndoReceiver.GO_ASYNC_BUDGET_MS - 1)

        assertTrue("onFinished must run before the 10s BroadcastReceiver ANR ceiling", finished)
        verify(exactly = 1) { notifier.showUndoResult(success = false) }

        coEvery { creator.undo("m1") } returns true
        runner.undo("m1")
        advanceUntilIdle()

        coVerify(exactly = 2) { creator.undo("m1") }
        verify(exactly = 1) { notifier.showUndoResult(success = true) }
    }
}
