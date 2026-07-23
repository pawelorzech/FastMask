package com.fastmask.data.repository

import android.app.Activity
import com.fastmask.data.billing.BillingDataSource
import com.fastmask.data.billing.BillingErrorCode
import com.fastmask.data.billing.BillingPurchase
import com.fastmask.data.billing.BillingResponse
import com.fastmask.data.local.ProEntitlementStore
import com.fastmask.di.ApplicationScope
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

/**
 * Owns all FastMask Pro entitlement logic:
 *
 * - seeds [proStatus] from the local cache, then reconciles against Play,
 * - acknowledges confirmed purchases (retried on the next refresh if it fails),
 * - keeps pending purchases locked ([ProStatus.PENDING] is not Pro),
 * - downgrades ONLY on an authoritative empty answer from Play — connection
 *   or availability errors keep the last verified state (offline support).
 */
@Singleton
class ProRepositoryImpl @Inject constructor(
    private val billing: BillingDataSource,
    private val store: ProEntitlementStore,
    private val analytics: MonetizationAnalytics,
    @ApplicationScope private val scope: CoroutineScope,
) : ProRepository {

    private val _proStatus = MutableStateFlow(ProStatus.FREE)
    override val proStatus: StateFlow<ProStatus> = _proStatus.asStateFlow()

    // Channel-backed one-time events — same pattern as the ViewModels: buffered
    // delivery survives collector gaps (rotation) and each event fires once.
    private val _events = Channel<ProPurchaseEvent>(Channel.BUFFERED)
    override val events: Flow<ProPurchaseEvent> = _events.receiveAsFlow()

    override fun drainPendingEvents() {
        while (_events.tryReceive().isSuccess) { /* discard stale */ }
    }

    /** Serializes every entitlement reconciliation (seed, refresh, buy flow). */
    private val reconcileMutex = Mutex()

    /** Cache seed — reconciliations wait for it so it can never clobber them. */
    private val seedJob = scope.launch {
        val cached = runCatching { store.read() }.getOrDefault(ProStatus.FREE)
        reconcileMutex.withLock {
            // compareAndSet semantics: never overwrite a value an authoritative
            // reconcile has already advanced past the initial FREE.
            if (_proStatus.value == ProStatus.FREE) {
                _proStatus.value = cached
            }
        }
    }

    init {
        scope.launch {
            seedJob.join()
            billing.purchaseUpdates.collect { update ->
                // A single failed update (DataStore I/O, closed channel…) must
                // not kill the collector for the rest of the process lifetime.
                runCatching { handleUpdate(update) }
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
                    // Already owned on this Play account (e.g. bought on
                    // another device) — reconcile, then report honestly.
                    refresh()
                    if (_proStatus.value == ProStatus.PRO) {
                        _events.send(ProPurchaseEvent.Completed)
                    } else {
                        _events.send(ProPurchaseEvent.Failed(update.code.name))
                    }
                }
                else -> {
                    analytics.track(
                        MonetizationEvent.PURCHASE_FAILED,
                        detail = update.code.name,
                    )
                    _events.send(ProPurchaseEvent.Failed(update.code.name))
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
                // Not authoritative — keep the cached entitlement untouched.
                if (response.code == BillingErrorCode.UNAVAILABLE) {
                    RefreshResult.UNAVAILABLE
                } else {
                    RefreshResult.ERROR
                }
            }
        }
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
                    refresh()
                    // Report ownership only when the reconcile confirmed it.
                    if (_proStatus.value == ProStatus.PRO) {
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
     * Reconcile entitlement from a purchase list. Only `queryPurchases`
     * ([fromBuyFlow] = false) is authoritative about ABSENCE: the buy-flow
     * listener reports the purchases of that update, not the account's full
     * holdings, so an OK update without our product must never downgrade.
     * [fromBuyFlow] additionally controls user-facing events.
     */
    private suspend fun handlePurchases(
        purchases: List<BillingPurchase>,
        fromBuyFlow: Boolean,
    ) = reconcileMutex.withLock {
        val proPurchase = purchases.firstOrNull { PRO_LIFETIME in it.productIds }
        if (fromBuyFlow && proPurchase == null) {
            // Not ours, not authoritative — leave the cached entitlement alone.
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
                        // Charged but not yet acknowledged: surface it as a
                        // pending (locked) state so the UI never hangs — the
                        // acknowledge retries on the next refresh.
                        is BillingResponse.Error -> ProStatus.PENDING
                    }
                }
            }
            proPurchase.isPending -> ProStatus.PENDING
            else -> ProStatus.FREE
        }

        if (newStatus != previous) {
            _proStatus.value = newStatus
            store.write(newStatus, proPurchase?.purchaseToken)
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
                ProStatus.FREE -> Unit
            }
        }
    }

    companion object {
        const val PRO_LIFETIME = "pro_lifetime"
    }
}
