package com.fastmask.quickmask

import android.content.Context
import com.fastmask.domain.usecase.QuickMaskCreator
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import com.fastmask.testutil.MainDispatcherRule
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import io.mockk.verify
import org.junit.Rule
import org.junit.Test

/**
 * `launchCreate` got an in-flight guard in an earlier audit because a Quick
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

        runner.launchUndo("m1")
        runner.launchUndo("m1")
        advanceUntilIdle()

        coVerify(exactly = 1) { creator.undo("m1") }
    }

    // The lie this guard exists to prevent: a second result overwriting the
    // first in the shared notification slot.
    @Test
    fun `a double-tapped undo reports its result once`() = runTest {
        coEvery { creator.undo("m1") } returns true
        val runner = runner()

        runner.launchUndo("m1")
        runner.launchUndo("m1")
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

        runner.launchUndo("m1")
        runner.launchUndo("m2")
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

        runner.launchUndo("m1")
        advanceUntilIdle()

        coEvery { creator.undo("m1") } returns true
        runner.launchUndo("m1")
        advanceUntilIdle()

        coVerify(exactly = 2) { creator.undo("m1") }
        verify(exactly = 1) { notifier.showUndoResult(success = true) }
    }

    @Test
    fun `the second tap still dismisses the notification it came from`() = runTest {
        coEvery { creator.undo("m1") } returns true
        val runner = runner()

        runner.launchUndo("m1")
        advanceUntilIdle()
        runner.launchUndo("m1")
        advanceUntilIdle()

        verify(exactly = 2) { notifier.cancel(QUICK_MASK_CREATED_NOTIFICATION_ID) }
    }
}
