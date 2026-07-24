package com.fastmask.data.repository

import com.fastmask.data.billing.BillingErrorCode
import com.fastmask.data.billing.BillingResponse
import com.fastmask.data.local.ProEntitlementStore
import com.fastmask.domain.model.ProStatus
import com.fastmask.domain.repository.ProPurchaseEvent
import com.fastmask.domain.repository.PurchaseLaunch
import com.fastmask.domain.repository.RefreshResult
import com.fastmask.domain.repository.RestoreResult
import com.fastmask.testutil.FakeBillingDataSource
import com.fastmask.testutil.FakeMonetizationAnalytics
import com.fastmask.testutil.billingPurchase
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ProRepositoryImplTest {

    private val billing = FakeBillingDataSource()
    private val analytics = FakeMonetizationAnalytics()
    private val store = mockk<ProEntitlementStore>(relaxed = true).also {
        coEvery { it.read() } returns ProStatus.FREE
    }

    // A scope on the test scheduler but with its own Job: advanceUntilIdle()
    // drives the repository's init coroutines (cache seed + purchase-updates
    // collector), while runTest's leak check ignores the still-running
    // collector at test end.
    private fun TestScope.repository(): ProRepositoryImpl =
        ProRepositoryImpl(
            billing,
            store,
            analytics,
            CoroutineScope(SupervisorJob() + StandardTestDispatcher(testScheduler)),
        )

    // --- refresh / entitlement reconciliation ---

    @Test
    fun `acknowledged purchase on refresh unlocks pro and persists`() = runTest {
        billing.purchasesResponse = BillingResponse.Ok(listOf(billingPurchase()))
        val repo = repository()
        advanceUntilIdle()

        assertEquals(RefreshResult.OK, repo.refresh())
        assertEquals(ProStatus.PRO, repo.proStatus.value)
        coVerify { store.write(ProStatus.PRO, "token-abc") }
    }

    @Test
    fun `unacknowledged purchase is acknowledged before unlocking`() = runTest {
        billing.purchasesResponse =
            BillingResponse.Ok(listOf(billingPurchase(acknowledged = false)))
        val repo = repository()
        advanceUntilIdle()

        repo.refresh()
        assertEquals(listOf("token-abc"), billing.acknowledgedTokens)
        assertEquals(ProStatus.PRO, repo.proStatus.value)
    }

    @Test
    fun `failed acknowledgement keeps purchase locked as PENDING until retried`() = runTest {
        billing.purchasesResponse =
            BillingResponse.Ok(listOf(billingPurchase(acknowledged = false)))
        billing.acknowledgeResponse = BillingResponse.Error(BillingErrorCode.NETWORK)
        val repo = repository()
        advanceUntilIdle()

        repo.refresh()
        // Charged but not acknowledged: locked, surfaced as PENDING (never a
        // silent hang, never an unlock before Play confirms).
        assertEquals(ProStatus.PENDING, repo.proStatus.value)

        // Next refresh (acknowledge succeeds) retries and unlocks.
        billing.acknowledgeResponse = BillingResponse.Ok(Unit)
        repo.refresh()
        assertEquals(ProStatus.PRO, repo.proStatus.value)
    }

    @Test
    fun `pending purchase stays locked as PENDING`() = runTest {
        billing.purchasesResponse =
            BillingResponse.Ok(listOf(billingPurchase(purchased = false, pending = true)))
        val repo = repository()
        advanceUntilIdle()

        repo.refresh()
        assertEquals(ProStatus.PENDING, repo.proStatus.value)
        assertTrue(billing.acknowledgedTokens.isEmpty())
    }

    @Test
    fun `authoritative empty answer downgrades cached pro`() = runTest {
        coEvery { store.read() } returns ProStatus.PRO
        billing.purchasesResponse = BillingResponse.Ok(emptyList())
        val repo = repository()
        advanceUntilIdle()
        assertEquals(ProStatus.PRO, repo.proStatus.value) // seeded from cache

        repo.refresh()
        assertEquals(ProStatus.FREE, repo.proStatus.value)
        coVerify { store.write(ProStatus.FREE, null) }
    }

    @Test
    fun `connection error keeps last verified entitlement`() = runTest {
        coEvery { store.read() } returns ProStatus.PRO
        billing.purchasesResponse = BillingResponse.Error(BillingErrorCode.NETWORK)
        val repo = repository()
        advanceUntilIdle()

        assertEquals(RefreshResult.ERROR, repo.refresh())
        assertEquals(ProStatus.PRO, repo.proStatus.value)
    }

    @Test
    fun `billing unavailable reports UNAVAILABLE and keeps entitlement`() = runTest {
        coEvery { store.read() } returns ProStatus.PRO
        billing.purchasesResponse = BillingResponse.Error(BillingErrorCode.UNAVAILABLE)
        val repo = repository()
        advanceUntilIdle()

        assertEquals(RefreshResult.UNAVAILABLE, repo.refresh())
        assertEquals(ProStatus.PRO, repo.proStatus.value)
    }

    // --- purchase flow ---

    @Test
    fun `purchase when already pro does not launch billing flow`() = runTest {
        coEvery { store.read() } returns ProStatus.PRO
        val repo = repository()
        advanceUntilIdle()

        assertEquals(PurchaseLaunch.ALREADY_OWNED, repo.purchase(mockk(relaxed = true)))
        assertEquals(0, billing.launchCalls)
    }

    @Test
    fun `purchase maps unavailable billing to UNAVAILABLE`() = runTest {
        billing.launchResponse = BillingResponse.Error(BillingErrorCode.UNAVAILABLE)
        val repo = repository()
        advanceUntilIdle()

        assertEquals(PurchaseLaunch.UNAVAILABLE, repo.purchase(mockk(relaxed = true)))
    }

    @Test
    fun `successful buy flow update unlocks pro and emits Completed`() = runTest {
        val repo = repository()
        advanceUntilIdle()

        billing.updates.emit(BillingResponse.Ok(listOf(billingPurchase())))
        advanceUntilIdle()

        assertEquals(ProStatus.PRO, repo.proStatus.value)
        assertEquals(ProPurchaseEvent.Completed, repo.events.first())
    }

    @Test
    fun `cancelled buy flow emits Cancelled and stays free`() = runTest {
        val repo = repository()
        advanceUntilIdle()

        billing.updates.emit(BillingResponse.Error(BillingErrorCode.USER_CANCELED))
        advanceUntilIdle()

        assertEquals(ProStatus.FREE, repo.proStatus.value)
        assertEquals(ProPurchaseEvent.Cancelled, repo.events.first())
    }

    @Test
    fun `pending buy flow emits Pending and stays locked`() = runTest {
        val repo = repository()
        advanceUntilIdle()

        billing.updates.emit(
            BillingResponse.Ok(listOf(billingPurchase(purchased = false, pending = true)))
        )
        advanceUntilIdle()

        assertEquals(ProStatus.PENDING, repo.proStatus.value)
        assertEquals(ProPurchaseEvent.Pending, repo.events.first())
    }

    @Test
    fun `buy flow OK without our product does not downgrade cached pro`() = runTest {
        // The PurchasesUpdatedListener reports only that update's purchases,
        // not the account's holdings — an OK update missing pro_lifetime is
        // NOT an authoritative "you own nothing" and must never clobber the
        // cached entitlement (only queryPurchases may downgrade).
        coEvery { store.read() } returns ProStatus.PRO
        val repo = repository()
        advanceUntilIdle()
        assertEquals(ProStatus.PRO, repo.proStatus.value)

        billing.updates.emit(BillingResponse.Ok(emptyList()))
        advanceUntilIdle()

        assertEquals(ProStatus.PRO, repo.proStatus.value)
        coVerify(exactly = 0) { store.write(ProStatus.FREE, any()) }
    }

    @Test
    fun `buy flow OK without our product still emits a terminal event`() = runTest {
        // Regression: the paywall's buy button clears its in-flight spinner only
        // when an event arrives. A buy-flow OK update lacking pro_lifetime must
        // still emit a terminal event, or the button spins forever.
        val repo = repository()
        advanceUntilIdle()

        billing.updates.emit(BillingResponse.Ok(emptyList()))
        advanceUntilIdle()

        assertTrue(repo.events.first() is ProPurchaseEvent.Failed)
    }

    // --- restore ---

    @Test
    fun `restore finds purchase`() = runTest {
        billing.purchasesResponse = BillingResponse.Ok(listOf(billingPurchase()))
        val repo = repository()
        advanceUntilIdle()

        assertEquals(RestoreResult.RESTORED, repo.restore())
        assertEquals(ProStatus.PRO, repo.proStatus.value)
    }

    @Test
    fun `restore with no purchase reports nothing to restore`() = runTest {
        val repo = repository()
        advanceUntilIdle()

        assertEquals(RestoreResult.NOTHING_TO_RESTORE, repo.restore())
    }

    @Test
    fun `restore without billing reports unavailable`() = runTest {
        billing.purchasesResponse = BillingResponse.Error(BillingErrorCode.UNAVAILABLE)
        val repo = repository()
        advanceUntilIdle()

        assertEquals(RestoreResult.UNAVAILABLE, repo.restore())
    }
}
