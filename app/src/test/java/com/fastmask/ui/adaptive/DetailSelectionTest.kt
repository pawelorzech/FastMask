@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)

package com.fastmask.ui.adaptive

import androidx.lifecycle.SavedStateHandle
import com.fastmask.testutil.FakeMaskedEmailRepository
import com.fastmask.testutil.MainDispatcherRule
import com.fastmask.testutil.mask
import com.fastmask.ui.detail.MaskedEmailDetailViewModel
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class DetailSelectionTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun `detail selection opens each id once and reads RAM cache on frame zero`() = runTest {
        val gate = CompletableDeferred<Unit>()
        val repo = FakeMaskedEmailRepository(
            emails = listOf(
                mask("m1", description = "First"),
                mask("m2", description = "Second"),
            ),
        ).apply {
            peeked = mask("m1", description = "First cached")
            beforeGetSingle = { gate.await() }
        }
        val vm = MaskedEmailDetailViewModel(SavedStateHandle(), repo)
        val selection = DetailSelection()

        selection.follow(vm, "m1")
        // Frame 0: RAM cache populated synchronously before network completes.
        assertEquals("m1", vm.uiState.value.email?.id)
        assertEquals("First cached", vm.uiState.value.email?.description)

        // Repeated recomposition with the same id must not re-fetch.
        selection.follow(vm, "m1")
        runCurrent()
        assertEquals(1, repo.getSingleCalls)

        // Switching to a second mask immediately replaces frame 0 with the new peeked mask.
        repo.peeked = mask("m2", description = "Second cached")
        selection.follow(vm, "m2")
        assertEquals("m2", vm.uiState.value.email?.id)
        assertEquals("Second cached", vm.uiState.value.email?.description)

        gate.complete(Unit)
        advanceUntilIdle()
        assertEquals("m2", vm.uiState.value.email?.id)
        assertEquals("Second", vm.uiState.value.email?.description)
    }
}
