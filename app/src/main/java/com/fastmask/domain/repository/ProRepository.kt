package com.fastmask.domain.repository

import android.app.Activity
import com.fastmask.domain.model.ProStatus
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

/**
 * Single source of truth for the FastMask Pro entitlement.
 *
 * Implementations must never unlock Pro from an unconfirmed purchase (pending
 * purchases stay [ProStatus.PENDING]) and must only downgrade on an
 * authoritative "no purchase" answer from Play — connection failures keep the
 * last verified state.
 */
interface ProRepository {

    /** Current entitlement. Seeded from local cache, refreshed from Play. */
    val proStatus: StateFlow<ProStatus>

    /** One-time purchase lifecycle events for UI feedback. */
    val events: Flow<ProPurchaseEvent>

    /** Re-query Play for purchases and reconcile the entitlement. */
    suspend fun refresh(): RefreshResult

    /** Product info for the paywall, or null when unavailable. */
    suspend fun getProduct(): ProProduct?

    /** Launch the Play purchase flow. Returns whether the flow started. */
    suspend fun purchase(activity: Activity): PurchaseLaunch

    /** Explicit "restore purchases" action. */
    suspend fun restore(): RestoreResult
}

/** Price and copy come formatted from Play — never hardcoded. */
data class ProProduct(
    val productId: String,
    val title: String,
    val description: String,
    val formattedPrice: String,
)

enum class RefreshResult { OK, UNAVAILABLE, ERROR }

enum class PurchaseLaunch { LAUNCHED, ALREADY_OWNED, UNAVAILABLE, ERROR }

enum class RestoreResult { RESTORED, NOTHING_TO_RESTORE, UNAVAILABLE, ERROR }

sealed class ProPurchaseEvent {
    data object Completed : ProPurchaseEvent()
    data object Pending : ProPurchaseEvent()
    data object Cancelled : ProPurchaseEvent()
    data class Failed(val code: String) : ProPurchaseEvent()
}
