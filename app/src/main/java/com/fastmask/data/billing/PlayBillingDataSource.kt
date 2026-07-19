package com.fastmask.data.billing

import android.app.Activity
import android.content.Context
import android.util.Log
import com.android.billingclient.api.AcknowledgePurchaseParams
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.BillingClientStateListener
import com.android.billingclient.api.BillingFlowParams
import com.android.billingclient.api.BillingResult
import com.android.billingclient.api.PendingPurchasesParams
import com.android.billingclient.api.ProductDetails
import com.android.billingclient.api.Purchase
import com.android.billingclient.api.QueryProductDetailsParams
import com.android.billingclient.api.QueryPurchasesParams
import com.fastmask.BuildConfig
import com.fastmask.di.ApplicationScope
import com.fastmask.domain.repository.ProProduct
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume

/**
 * Google Play implementation. One [BillingClient] for the process lifetime,
 * created lazily off the main thread, with automatic service reconnection.
 * Uses the base (Java) Billing 8.x artifact with hand-rolled suspend wrappers
 * — see build.gradle.kts for why billing-ktx is not used.
 */
@Singleton
class PlayBillingDataSource @Inject constructor(
    @ApplicationContext private val context: Context,
    @ApplicationScope private val scope: CoroutineScope,
) : BillingDataSource {

    // replay=1 closes a narrow startup window: an update arriving before the
    // repository's collector subscribes would otherwise be dropped silently.
    private val _purchaseUpdates =
        MutableSharedFlow<BillingResponse<List<BillingPurchase>>>(
            replay = 1,
            extraBufferCapacity = 8,
        )
    override val purchaseUpdates: SharedFlow<BillingResponse<List<BillingPurchase>>> =
        _purchaseUpdates.asSharedFlow()

    /** ProductDetails must be passed back to launchBillingFlow — cache by id. */
    private val productDetailsCache = ConcurrentHashMap<String, ProductDetails>()

    private val client: BillingClient by lazy {
        BillingClient.newBuilder(context)
            .setListener { billingResult, purchases ->
                val response = if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                    BillingResponse.Ok(purchases.orEmpty().map { it.toBillingPurchase() })
                } else {
                    BillingResponse.Error(billingResult.toErrorCode())
                }
                // Listener fires on the main thread; hand off to the app scope.
                scope.launch { _purchaseUpdates.emit(response) }
            }
            .enablePendingPurchases(
                PendingPurchasesParams.newBuilder().enableOneTimeProducts().build()
            )
            .enableAutoServiceReconnection()
            .build()
    }

    private val connectionMutex = Mutex()

    private suspend fun ensureConnected(): BillingResponse<Unit> = withContext(Dispatchers.IO) {
        connectionMutex.withLock {
            if (client.isReady) return@withLock BillingResponse.Ok(Unit)
            // Timeout guards against a wedged Play Store never delivering the
            // setup callback — without it the mutex would deadlock every
            // future billing call in the process.
            withTimeoutOrNull(BILLING_CALL_TIMEOUT_MS) {
                suspendCancellableCoroutine { continuation ->
                    client.startConnection(object : BillingClientStateListener {
                        override fun onBillingSetupFinished(billingResult: BillingResult) {
                            if (continuation.isActive) {
                                continuation.resume(
                                    if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                                        BillingResponse.Ok(Unit)
                                    } else {
                                        BillingResponse.Error(billingResult.toErrorCode())
                                    }
                                )
                            }
                        }

                        override fun onBillingServiceDisconnected() {
                            // enableAutoServiceReconnection() retries for later calls;
                            // an in-flight startConnection failure surfaces via
                            // onBillingSetupFinished, so nothing to do here.
                        }
                    })
                }
            } ?: BillingResponse.Error(BillingErrorCode.UNAVAILABLE)
        }
    }

    override suspend fun queryProduct(productId: String): BillingResponse<ProProduct?> {
        val connection = ensureConnected()
        if (connection is BillingResponse.Error) return connection

        val params = QueryProductDetailsParams.newBuilder()
            .setProductList(
                listOf(
                    QueryProductDetailsParams.Product.newBuilder()
                        .setProductId(productId)
                        .setProductType(BillingClient.ProductType.INAPP)
                        .build()
                )
            )
            .build()

        return withTimeoutOrNull(BILLING_CALL_TIMEOUT_MS) {
            suspendCancellableCoroutine { continuation ->
            client.queryProductDetailsAsync(params) { billingResult, result ->
                if (!continuation.isActive) return@queryProductDetailsAsync
                if (billingResult.responseCode != BillingClient.BillingResponseCode.OK) {
                    continuation.resume(BillingResponse.Error(billingResult.toErrorCode()))
                    return@queryProductDetailsAsync
                }
                val details = result.productDetailsList.firstOrNull { it.productId == productId }
                if (details == null) {
                    continuation.resume(BillingResponse.Ok(null))
                } else {
                    productDetailsCache[productId] = details
                    val price = details.oneTimePurchaseOfferDetails?.formattedPrice
                    continuation.resume(
                        if (price == null) {
                            // A one-time product without an offer is a store
                            // misconfiguration — treat as not purchasable.
                            BillingResponse.Ok(null)
                        } else {
                            BillingResponse.Ok(
                                ProProduct(
                                    productId = details.productId,
                                    title = details.title,
                                    description = details.description,
                                    formattedPrice = price,
                                )
                            )
                        }
                    )
                }
            }
            }
        } ?: BillingResponse.Error(BillingErrorCode.NETWORK)
    }

    override suspend fun queryPurchases(): BillingResponse<List<BillingPurchase>> {
        val connection = ensureConnected()
        if (connection is BillingResponse.Error) return connection

        val params = QueryPurchasesParams.newBuilder()
            .setProductType(BillingClient.ProductType.INAPP)
            .build()

        return withTimeoutOrNull(BILLING_CALL_TIMEOUT_MS) {
            suspendCancellableCoroutine { continuation ->
                client.queryPurchasesAsync(params) { billingResult, purchases ->
                    if (!continuation.isActive) return@queryPurchasesAsync
                    continuation.resume(
                        if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                            BillingResponse.Ok(purchases.map { it.toBillingPurchase() })
                        } else {
                            BillingResponse.Error(billingResult.toErrorCode())
                        }
                    )
                }
            }
        } ?: BillingResponse.Error(BillingErrorCode.NETWORK)
    }

    override suspend fun acknowledge(purchaseToken: String): BillingResponse<Unit> {
        val connection = ensureConnected()
        if (connection is BillingResponse.Error) return connection

        val params = AcknowledgePurchaseParams.newBuilder()
            .setPurchaseToken(purchaseToken)
            .build()

        return withTimeoutOrNull(BILLING_CALL_TIMEOUT_MS) {
            suspendCancellableCoroutine { continuation ->
                client.acknowledgePurchase(params) { billingResult ->
                    if (!continuation.isActive) return@acknowledgePurchase
                    continuation.resume(
                        if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                            BillingResponse.Ok(Unit)
                        } else {
                            BillingResponse.Error(billingResult.toErrorCode())
                        }
                    )
                }
            }
        } ?: BillingResponse.Error(BillingErrorCode.NETWORK)
    }

    override suspend fun launchBillingFlow(
        activity: Activity,
        productId: String,
    ): BillingResponse<Unit> {
        // ProductDetails outlives a service disconnect — a cached hit must not
        // skip the connection check or launch fails with a spurious error.
        val connection = ensureConnected()
        if (connection is BillingResponse.Error) return connection

        val details = productDetailsCache[productId] ?: run {
            when (val queried = queryProduct(productId)) {
                is BillingResponse.Error -> return BillingResponse.Error(queried.code)
                is BillingResponse.Ok ->
                    productDetailsCache[productId]
                        ?: return BillingResponse.Error(BillingErrorCode.DEVELOPER)
            }
        }

        val params = BillingFlowParams.newBuilder()
            .setProductDetailsParamsList(
                listOf(
                    BillingFlowParams.ProductDetailsParams.newBuilder()
                        .setProductDetails(details)
                        .build()
                )
            )
            .build()

        // Must be called on the main thread with a foreground Activity.
        val result = withContext(Dispatchers.Main) { client.launchBillingFlow(activity, params) }
        return if (result.responseCode == BillingClient.BillingResponseCode.OK) {
            BillingResponse.Ok(Unit)
        } else {
            BillingResponse.Error(result.toErrorCode())
        }
    }

    private fun Purchase.toBillingPurchase() = BillingPurchase(
        productIds = products,
        isPurchased = purchaseState == Purchase.PurchaseState.PURCHASED,
        isPending = purchaseState == Purchase.PurchaseState.PENDING,
        isAcknowledged = isAcknowledged,
        purchaseToken = purchaseToken,
    )

    private fun BillingResult.toErrorCode(): BillingErrorCode {
        if (BuildConfig.DEBUG) {
            // Response code only — never tokens or order ids.
            Log.d("FastMaskBilling", "Billing error: $responseCode $debugMessage")
        }
        return when (responseCode) {
            BillingClient.BillingResponseCode.USER_CANCELED -> BillingErrorCode.USER_CANCELED
            BillingClient.BillingResponseCode.BILLING_UNAVAILABLE,
            BillingClient.BillingResponseCode.SERVICE_UNAVAILABLE,
            BillingClient.BillingResponseCode.SERVICE_DISCONNECTED,
            BillingClient.BillingResponseCode.FEATURE_NOT_SUPPORTED,
            -> BillingErrorCode.UNAVAILABLE

            BillingClient.BillingResponseCode.NETWORK_ERROR -> BillingErrorCode.NETWORK
            BillingClient.BillingResponseCode.ITEM_ALREADY_OWNED -> BillingErrorCode.ITEM_ALREADY_OWNED
            BillingClient.BillingResponseCode.ITEM_UNAVAILABLE,
            BillingClient.BillingResponseCode.DEVELOPER_ERROR,
            -> BillingErrorCode.DEVELOPER

            else -> BillingErrorCode.OTHER
        }
    }

    private companion object {
        /** Billing callbacks normally fire in ms; this only catches a wedged Play Store. */
        const val BILLING_CALL_TIMEOUT_MS = 15_000L
    }
}
