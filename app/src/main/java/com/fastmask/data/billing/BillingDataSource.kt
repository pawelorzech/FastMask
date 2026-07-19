package com.fastmask.data.billing

import android.app.Activity
import com.fastmask.domain.repository.ProProduct
import kotlinx.coroutines.flow.SharedFlow

/**
 * Thin, testable abstraction over Google Play Billing. Maps every Play type to
 * plain Kotlin so [com.fastmask.data.repository.ProRepositoryImpl] (which owns
 * all entitlement logic) can be unit-tested against a fake.
 */
interface BillingDataSource {

    /** Emissions from Play's PurchasesUpdatedListener (buy flow results). */
    val purchaseUpdates: SharedFlow<BillingResponse<List<BillingPurchase>>>

    suspend fun queryProduct(productId: String): BillingResponse<ProProduct?>

    /** Active one-time purchases owned by the current Play account. */
    suspend fun queryPurchases(): BillingResponse<List<BillingPurchase>>

    suspend fun acknowledge(purchaseToken: String): BillingResponse<Unit>

    /** Ok means the Play sheet was launched; the outcome arrives on [purchaseUpdates]. */
    suspend fun launchBillingFlow(activity: Activity, productId: String): BillingResponse<Unit>
}

data class BillingPurchase(
    val productIds: List<String>,
    val isPurchased: Boolean,
    val isPending: Boolean,
    val isAcknowledged: Boolean,
    val purchaseToken: String,
)

sealed class BillingResponse<out T> {
    data class Ok<T>(val value: T) : BillingResponse<T>()
    data class Error(val code: BillingErrorCode) : BillingResponse<Nothing>()
}

enum class BillingErrorCode {
    USER_CANCELED,

    /** No Play Store / billing not supported (e.g. sideloaded GitHub build). */
    UNAVAILABLE,

    /** Transient network problem — retry later, keep cached entitlement. */
    NETWORK,

    ITEM_ALREADY_OWNED,

    /** Product not found / misconfiguration — actionable for the developer. */
    DEVELOPER,

    OTHER,
}
