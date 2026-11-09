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

    val proStatus: StateFlow<ProStatus>

    val events: Flow<ProPurchaseEvent>

    fun drainPendingEvents()

    // So the first frame can be themed for a Pro user before Play has answered.
    suspend fun awaitCached()

    suspend fun refresh(): RefreshResult

    suspend fun getProduct(): ProProduct?

    suspend fun purchase(activity: Activity): PurchaseLaunch

    suspend fun restore(): RestoreResult
}

/** The price comes formatted from Play — never hardcoded. */
data class ProProduct(
    val formattedPrice: String,
)

enum class RefreshResult { OK, UNAVAILABLE, ERROR }

enum class PurchaseLaunch { LAUNCHED, ALREADY_OWNED, UNAVAILABLE, ERROR }

enum class RestoreResult { RESTORED, NOTHING_TO_RESTORE, UNAVAILABLE, ERROR }

sealed class ProPurchaseEvent {
    data object Completed : ProPurchaseEvent()
    data object Pending : ProPurchaseEvent()
    data object Cancelled : ProPurchaseEvent()
    data object Failed : ProPurchaseEvent()
}
