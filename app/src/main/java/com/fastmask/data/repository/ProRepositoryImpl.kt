package com.fastmask.data.repository

import android.app.Activity
import com.fastmask.core.runSuspendCatching
import com.fastmask.data.billing.BillingDataSource
import com.fastmask.data.billing.BillingErrorCode
import com.fastmask.data.billing.BillingPurchase
import com.fastmask.data.billing.BillingResponse
import com.fastmask.data.local.ProEntitlementStore
import com.fastmask.core.ApplicationScope
import com.fastmask.domain.analytics.MonetizationAnalytics
import com.fastmask.domain.analytics.MonetizationEvent
import com.fastmask.domain.model.ProStatus
import com.fastmask.domain.repository.ProProduct
import com.fastmask.domain.repository.ProPurchaseEvent
import com.fastmask.domain.repository.ProRepository
import com.fastmask.domain.repository.PurchaseLaunch
import com.fastmask.domain.repository.RefreshResult
import com.fastmask.domain.repository.RestoreResult
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ProRepositoryImpl @Inject constructor(
    private val billing: BillingDataSource,
    private val store: ProEntitlementStore,
    private val analytics: MonetizationAnalytics,
    @ApplicationScope private val scope: CoroutineScope,
) : ProRepository {

    private val _proStatus = MutableStateFlow(ProStatus.FREE)
    override val proStatus: StateFlow<ProStatus> = _proStatus.asStateFlow()

    private val _events = Channel<ProPurchaseEvent>(Channel.BUFFERED)
    override val events: Flow<ProPurchaseEvent> = _events.receiveAsFlow()

    override fun drainPendingEvents() {
        while (_events.tryReceive().isSuccess) { /* discard stale */ }
    }

    private val reconcileMutex = Mutex()

    private var lastPersistedToken: String? = null

    private val seedJob = scope.launch {
        val cached = runSuspendCatching { store.read() }.getOrDefault(ProStatus.FREE)
        reconcileMutex.withLock {
            if (_proStatus.value == ProStatus.FREE) {
                _proStatus.value = cached
            }
        }
    }

    override suspend fun awaitCached() = seedJob.join()

    init {
        scope.launch {
            seedJob.join()
            billing.purchaseUpdates.collect { update ->
                runSuspendCatching { handleUpdate(update) }
            }
        }
    }

    private suspend fun handleUpdate(update: BillingResponse<List<BillingPurchase>>) {
        when (update) {
            is BillingResponse.Ok -> handlePurchases(update.value, fromBuyFlow = true)
            is BillingResponse.Error -> when (update.code) {
                BillingErrorCode.USER_CANCELED -> {
                    analytics.track(MonetizationEvent.PURCHASE_CANCELLED)
                    _events.send(ProPurchaseEvent.Cancelled)
                }
                BillingErrorCode.ITEM_ALREADY_OWNED -> {
                    // Already owned on this Play account (e.g. bought on another device).
                    if (ownershipConfirmedByRefresh()) {
                        _events.send(ProPurchaseEvent.Completed)
                    } else {
                        _events.send(ProPurchaseEvent.Failed)
                    }
                }
                else -> {
                    analytics.track(
                        MonetizationEvent.PURCHASE_FAILED,
                        detail = update.code.name,
                    )
                    _events.send(ProPurchaseEvent.Failed)
                }
            }
        }
    }

    override suspend fun refresh(): RefreshResult {
        seedJob.join()
        return when (val response = billing.queryPurchases()) {
            is BillingResponse.Ok -> {
                handlePurchases(response.value, fromBuyFlow = false)
                RefreshResult.OK
            }
            is BillingResponse.Error -> {
                if (response.code == BillingErrorCode.UNAVAILABLE) {
                    RefreshResult.UNAVAILABLE
                } else {
                    RefreshResult.ERROR
                }
            }
        }
    }

    private suspend fun ownershipConfirmedByRefresh(): Boolean {
        refresh()
        return _proStatus.value == ProStatus.PRO
    }

    override suspend fun getProduct(): ProProduct? =
        when (val response = billing.queryProduct(PRO_LIFETIME)) {
            is BillingResponse.Ok -> response.value
            is BillingResponse.Error -> null
        }

    override suspend fun purchase(activity: Activity): PurchaseLaunch {
        if (_proStatus.value == ProStatus.PRO) return PurchaseLaunch.ALREADY_OWNED
        analytics.track(MonetizationEvent.PURCHASE_STARTED)
        return when (val response = billing.launchBillingFlow(activity, PRO_LIFETIME)) {
            is BillingResponse.Ok -> PurchaseLaunch.LAUNCHED
            is BillingResponse.Error -> when (response.code) {
                BillingErrorCode.ITEM_ALREADY_OWNED -> {
                    if (ownershipConfirmedByRefresh()) {
                        PurchaseLaunch.ALREADY_OWNED
                    } else {
                        PurchaseLaunch.ERROR
                    }
                }
                BillingErrorCode.UNAVAILABLE -> PurchaseLaunch.UNAVAILABLE
                else -> PurchaseLaunch.ERROR
            }
        }
    }

    override suspend fun restore(): RestoreResult {
        val result = refresh()
        return when {
            result == RefreshResult.UNAVAILABLE -> RestoreResult.UNAVAILABLE
            result == RefreshResult.ERROR -> RestoreResult.ERROR
            _proStatus.value == ProStatus.PRO -> {
                analytics.track(MonetizationEvent.PURCHASE_RESTORED)
                RestoreResult.RESTORED
            }
            else -> RestoreResult.NOTHING_TO_RESTORE
        }
    }

    /**
     * Only `queryPurchases` ([fromBuyFlow] = false) is authoritative about
     * ABSENCE: the buy-flow listener reports the purchases of that update, not the
     * account's full holdings, so an OK update without our product must never
     * downgrade.
     */
    private suspend fun handlePurchases(
        purchases: List<BillingPurchase>,
        fromBuyFlow: Boolean,
    ) = reconcileMutex.withLock {
        val proPurchase = purchases.firstOrNull { PRO_LIFETIME in it.productIds }
        if (fromBuyFlow && proPurchase == null) {
            _events.send(ProPurchaseEvent.Failed)
            return@withLock
        }
        val previous = _proStatus.value

        val newStatus = when {
            proPurchase == null -> ProStatus.FREE
            proPurchase.isPurchased -> {
                if (proPurchase.isAcknowledged) {
                    ProStatus.PRO
                } else {
                    // Unlock only after Play confirms the acknowledgement; an
                    // unacknowledged purchase is refunded by Play in ~3 days.
                    when (billing.acknowledge(proPurchase.purchaseToken)) {
                        is BillingResponse.Ok -> ProStatus.PRO
                            is BillingResponse.Error -> ProStatus.PENDING
                    }
                }
            }
            proPurchase.isPending -> ProStatus.PENDING
            else -> ProStatus.FREE
        }

        val token = proPurchase?.purchaseToken
        if (newStatus != previous || token != lastPersistedToken) {
            _proStatus.value = newStatus
            runSuspendCatching {
                store.write(newStatus, token)
                lastPersistedToken = token
            }.onFailure {
                // Retried on the next reconciliation.
            }
        }
        if (newStatus != previous) {
            when {
                newStatus == ProStatus.PRO -> {
                    analytics.track(MonetizationEvent.ENTITLEMENT_ACTIVATED)
                    if (fromBuyFlow) {
                        analytics.track(MonetizationEvent.PURCHASE_COMPLETED)
                    }
                }
                previous == ProStatus.PRO -> analytics.track(MonetizationEvent.ENTITLEMENT_EXPIRED)
            }
        }

        if (fromBuyFlow) {
            when (newStatus) {
                ProStatus.PRO -> _events.send(ProPurchaseEvent.Completed)
                ProStatus.PENDING -> {
                    analytics.track(MonetizationEvent.PURCHASE_PENDING)
                    _events.send(ProPurchaseEvent.Pending)
                }
                ProStatus.FREE -> _events.send(ProPurchaseEvent.Failed)
            }
        }
    }

    companion object {
        const val PRO_LIFETIME = "pro_lifetime"
    }
}
