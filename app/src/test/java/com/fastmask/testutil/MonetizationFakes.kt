package com.fastmask.testutil

import android.app.Activity
import com.fastmask.data.billing.BillingDataSource
import com.fastmask.data.billing.BillingPurchase
import com.fastmask.data.billing.BillingResponse
import com.fastmask.domain.analytics.MonetizationAnalytics
import com.fastmask.domain.analytics.MonetizationEvent
import com.fastmask.domain.model.ProStatus
import com.fastmask.domain.repository.ProProduct
import com.fastmask.domain.repository.ProPurchaseEvent
import com.fastmask.domain.repository.ProRepository
import com.fastmask.domain.repository.PurchaseLaunch
import com.fastmask.domain.repository.RefreshResult
import com.fastmask.domain.repository.RestoreResult
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow

fun billingPurchase(
    productId: String = "pro_lifetime",
    purchased: Boolean = true,
    pending: Boolean = false,
    acknowledged: Boolean = true,
    token: String = "token-abc",
) = BillingPurchase(
    productIds = listOf(productId),
    isPurchased = purchased,
    isPending = pending,
    isAcknowledged = acknowledged,
    purchaseToken = token,
)

val testProduct = ProProduct(
    productId = "pro_lifetime",
    title = "FastMask Pro",
    description = "Accents, app lock, CSV export",
    formattedPrice = "€4.99",
)

class FakeBillingDataSource : BillingDataSource {

    val updates = MutableSharedFlow<BillingResponse<List<BillingPurchase>>>(extraBufferCapacity = 8)
    override val purchaseUpdates: SharedFlow<BillingResponse<List<BillingPurchase>>> = updates

    var productResponse: BillingResponse<ProProduct?> = BillingResponse.Ok(testProduct)
    var purchasesResponse: BillingResponse<List<BillingPurchase>> = BillingResponse.Ok(emptyList())
    var acknowledgeResponse: BillingResponse<Unit> = BillingResponse.Ok(Unit)
    var launchResponse: BillingResponse<Unit> = BillingResponse.Ok(Unit)

    val acknowledgedTokens = mutableListOf<String>()
    var launchCalls = 0
    var queryPurchasesCalls = 0

    override suspend fun queryProduct(productId: String): BillingResponse<ProProduct?> =
        productResponse

    override suspend fun queryPurchases(): BillingResponse<List<BillingPurchase>> {
        queryPurchasesCalls++
        return purchasesResponse
    }

    override suspend fun acknowledge(purchaseToken: String): BillingResponse<Unit> {
        acknowledgedTokens += purchaseToken
        return acknowledgeResponse
    }

    override suspend fun launchBillingFlow(
        activity: Activity,
        productId: String,
    ): BillingResponse<Unit> {
        launchCalls++
        return launchResponse
    }
}

class FakeProRepository(
    initialStatus: ProStatus = ProStatus.FREE,
) : ProRepository {

    val statusFlow = MutableStateFlow(initialStatus)
    override val proStatus: StateFlow<ProStatus> = statusFlow.asStateFlow()

    val eventsChannel = Channel<ProPurchaseEvent>(Channel.BUFFERED)
    override val events: Flow<ProPurchaseEvent> = eventsChannel.receiveAsFlow()

    var drainCalls = 0
    override fun drainPendingEvents() {
        drainCalls++
        while (eventsChannel.tryReceive().isSuccess) { /* discard */ }
    }

    var product: ProProduct? = testProduct
    var refreshResult: RefreshResult = RefreshResult.OK
    var purchaseLaunch: PurchaseLaunch = PurchaseLaunch.LAUNCHED
    var restoreResult: RestoreResult = RestoreResult.NOTHING_TO_RESTORE

    var refreshCalls = 0
    var purchaseCalls = 0
    var restoreCalls = 0

    override suspend fun refresh(): RefreshResult {
        refreshCalls++
        return refreshResult
    }

    override suspend fun getProduct(): ProProduct? = product

    override suspend fun purchase(activity: Activity): PurchaseLaunch {
        purchaseCalls++
        return purchaseLaunch
    }

    override suspend fun restore(): RestoreResult {
        restoreCalls++
        return restoreResult
    }
}

class FakeMonetizationAnalytics : MonetizationAnalytics {
    data class Tracked(val event: MonetizationEvent, val source: String?, val detail: String?)

    val tracked = mutableListOf<Tracked>()

    override fun track(event: MonetizationEvent, source: String?, detail: String?) {
        tracked += Tracked(event, source, detail)
    }

    fun events(): List<MonetizationEvent> = tracked.map { it.event }
}
