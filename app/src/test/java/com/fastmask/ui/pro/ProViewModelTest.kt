package com.fastmask.ui.pro

import android.app.Activity
import androidx.lifecycle.SavedStateHandle
import com.fastmask.domain.analytics.MonetizationEvent
import com.fastmask.domain.model.ProStatus
import com.fastmask.domain.repository.ProPurchaseEvent
import com.fastmask.domain.repository.PurchaseLaunch
import com.fastmask.domain.repository.RestoreResult
import com.fastmask.testutil.FakeMonetizationAnalytics
import com.fastmask.testutil.FakeProRepository
import com.fastmask.testutil.MainDispatcherRule
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ProViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val repository = FakeProRepository()
    private val analytics = FakeMonetizationAnalytics()
    private val activity = mockk<Activity>(relaxed = true)

    private fun viewModel(source: String = "settings") = ProViewModel(
        proRepository = repository,
        analytics = analytics,
        savedStateHandle = SavedStateHandle(mapOf("source" to source)),
    )

    @Test
    fun `stale events buffered before the paywall opened are drained not replayed`() = runTest {
        // A purchase resolution from a long-closed session must not fire a
        // snackbar days later — entitlement state travels via proStatus.
        repository.eventsChannel.trySend(ProPurchaseEvent.Cancelled)
        val vm = viewModel()
        advanceUntilIdle()

        assertEquals(1, repository.drainCalls)
        val events = mutableListOf<ProUiEvent>()
        val job = launch { vm.events.collect { events.add(it) } }
        advanceUntilIdle()
        assertEquals(emptyList<ProUiEvent>(), events)
        job.cancel()
    }

    @Test
    fun `paywall view is tracked with its source`() = runTest {
        viewModel(source = "accent")
        assertEquals(
            listOf(FakeMonetizationAnalytics.Tracked(MonetizationEvent.PAYWALL_VIEWED, "accent", null)),
            analytics.tracked,
        )
    }

    @Test
    fun `product loads into READY state with formatted price`() = runTest {
        val vm = viewModel()
        advanceUntilIdle()

        assertEquals(ProductState.READY, vm.uiState.value.productState)
        assertEquals("€4.99", vm.uiState.value.product?.formattedPrice)
        assertEquals(1, repository.refreshCalls)
    }

    @Test
    fun `missing product yields UNAVAILABLE state`() = runTest {
        repository.product = null
        val vm = viewModel()
        advanceUntilIdle()

        assertEquals(ProductState.UNAVAILABLE, vm.uiState.value.productState)
    }

    @Test
    fun `rapid double tap launches exactly one purchase flow`() = runTest {
        val vm = viewModel()
        advanceUntilIdle()

        // Both taps land before the coroutine dispatches — the synchronous
        // in-flight guard must absorb the second one.
        vm.buy(activity)
        vm.buy(activity)
        advanceUntilIdle()

        assertEquals(1, repository.purchaseCalls)
    }

    @Test
    fun `unavailable billing surfaces BillingUnavailable and clears in-flight`() = runTest {
        repository.purchaseLaunch = PurchaseLaunch.UNAVAILABLE
        val vm = viewModel()
        advanceUntilIdle()

        vm.buy(activity)
        advanceUntilIdle()

        assertEquals(ProUiEvent.BillingUnavailable, vm.events.first())
        assertFalse(vm.uiState.value.purchaseInFlight)
    }

    @Test
    fun `repository Completed event becomes PurchaseCompleted and clears in-flight`() = runTest {
        val vm = viewModel()
        advanceUntilIdle()

        vm.buy(activity)
        advanceUntilIdle()
        repository.eventsChannel.send(ProPurchaseEvent.Completed)
        advanceUntilIdle()

        assertEquals(ProUiEvent.PurchaseCompleted, vm.events.first())
        assertFalse(vm.uiState.value.purchaseInFlight)
    }

    @Test
    fun `pro status flows into ui state`() = runTest {
        val vm = viewModel()
        advanceUntilIdle()

        repository.statusFlow.value = ProStatus.PRO
        advanceUntilIdle()

        assertEquals(ProStatus.PRO, vm.uiState.value.status)
    }

    @Test
    fun `restore outcomes map to ui events`() = runTest {
        repository.restoreResult = RestoreResult.NOTHING_TO_RESTORE
        val vm = viewModel()
        advanceUntilIdle()

        vm.restore()
        advanceUntilIdle()

        assertEquals(ProUiEvent.NothingToRestore, vm.events.first())
    }

    @Test
    fun `paywall close is tracked from onCleared so gesture back counts too`() = runTest {
        val vm = viewModel()
        advanceUntilIdle()

        // onCleared is framework-invoked; clearing the owning store is the
        // public path that triggers it (same as any paywall dismissal).
        androidx.lifecycle.ViewModelStore().apply {
            put("vm", vm)
            clear()
        }

        assertEquals(
            MonetizationEvent.PAYWALL_CLOSED,
            analytics.tracked.last().event,
        )
    }
}
