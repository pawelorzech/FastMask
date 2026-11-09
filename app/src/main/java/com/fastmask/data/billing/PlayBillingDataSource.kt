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
import com.android.billingclient.api.UnfetchedProduct
import com.fastmask.BuildConfig
import com.fastmask.core.ApplicationScope
import com.fastmask.core.IoDispatcher
import com.fastmask.domain.repository.ProProduct
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
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
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume

@Singleton
class PlayBillingDataSource @Inject constructor(
    @ApplicationContext private val context: Context,
    @ApplicationScope private val scope: CoroutineScope,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) : BillingDataSource {

    private val _purchaseUpdates =
        MutableSharedFlow<BillingResponse<List<BillingPurchase>>>(
            replay = 1,
            extraBufferCapacity = 8,
        )
    override val purchaseUpdates: SharedFlow<BillingResponse<List<BillingPurchase>>> =
        _purchaseUpdates.asSharedFlow()

    private val client: BillingClient by lazy {
        BillingClient.newBuilder(context)
            .setListener { billingResult, purchases ->
                val response = if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                    BillingResponse.Ok(purchases.orEmpty().map { it.toBillingPurchase() })
                } else {
                    BillingResponse.Error(billingResult.toErrorCode())
                }
                // Launching a coroutine per callback can reorder two rapid updates
                // (e.g. PENDING overtaking PURCHASED).
                _purchaseUpdates.tryEmit(response)
            }
            .enablePendingPurchases(
                PendingPurchasesParams.newBuilder().enableOneTimeProducts().build()
            )
            .enableAutoServiceReconnection()
            .build()
    }

    private val connectionMutex = Mutex()

    private suspend fun ensureConnected(): BillingResponse<Unit> = withContext(ioDispatcher) {
        connectionMutex.withLock {
            if (client.isReady) return@withLock BillingResponse.Ok(Unit)
            // A wedged Play Store may never deliver the setup callback.
            billingCall<Unit>(BillingErrorCode.UNAVAILABLE) { respond ->
                client.startConnection(object : BillingClientStateListener {
                    override fun onBillingSetupFinished(billingResult: BillingResult) {
                        respond(billingResult.toResponse { Unit })
                    }

                    override fun onBillingServiceDisconnected() {
                        // enableAutoServiceReconnection() retries for later calls;
                        // an in-flight startConnection failure surfaces via
                        // onBillingSetupFinished, so nothing to do here.
                    }
                })
            }
        }
    }

    private suspend fun <T> billingCall(
        timeoutFallback: BillingErrorCode,
        register: (respond: (BillingResponse<T>) -> Unit) -> Unit,
    ): BillingResponse<T> = withTimeoutOrNull(BILLING_CALL_TIMEOUT_MS) {
        suspendCancellableCoroutine<BillingResponse<T>> { continuation ->
            register { response -> if (continuation.isActive) continuation.resume(response) }
        }
    } ?: BillingResponse.Error(timeoutFallback)

    private inline fun <T> BillingResult.toResponse(value: () -> T): BillingResponse<T> =
        if (responseCode == BillingClient.BillingResponseCode.OK) {
            BillingResponse.Ok(value())
        } else {
            BillingResponse.Error(toErrorCode())
        }

    override suspend fun queryProduct(productId: String): BillingResponse<ProProduct?> =
        when (val response = queryFreshProductDetails(productId)) {
            is BillingResponse.Error -> response
            is BillingResponse.Ok -> BillingResponse.Ok(response.value?.toProProduct())
        }

    /**
     * ProductDetails are deliberately never cached. Google warns that stale
     * instances can make launchBillingFlow fail, so the buy action queries a
     * fresh instance immediately before handing it back to Play.
     */
    private suspend fun queryFreshProductDetails(productId: String): BillingResponse<ProductDetails?> {
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

        return billingCall(BillingErrorCode.NETWORK) { respond ->
            client.queryProductDetailsAsync(params) { billingResult, result ->
                if (billingResult.responseCode != BillingClient.BillingResponseCode.OK) {
                    respond(BillingResponse.Error(billingResult.toErrorCode()))
                    return@queryProductDetailsAsync
                }
                val details = result.productDetailsList.firstOrNull { it.productId == productId }
                respond(
                    if (details != null) {
                        BillingResponse.Ok(details)
                    } else {
                        val unfetched = result.unfetchedProductList.firstOrNull { it.productId == productId }
                        if (unfetched == null) {
                            BillingResponse.Ok(null)
                        } else {
                            BillingResponse.Error(unfetched.statusCode.toBillingErrorCode())
                        }
                    }
                )
            }
        }
    }

    override suspend fun queryPurchases(): BillingResponse<List<BillingPurchase>> {
        val connection = ensureConnected()
        if (connection is BillingResponse.Error) return connection

        val params = QueryPurchasesParams.newBuilder()
            .setProductType(BillingClient.ProductType.INAPP)
            .build()

        return billingCall(BillingErrorCode.NETWORK) { respond ->
            client.queryPurchasesAsync(params) { billingResult, purchases ->
                respond(billingResult.toResponse { purchases.map { it.toBillingPurchase() } })
            }
        }
    }

    override suspend fun acknowledge(purchaseToken: String): BillingResponse<Unit> {
        val connection = ensureConnected()
        if (connection is BillingResponse.Error) return connection

        val params = AcknowledgePurchaseParams.newBuilder()
            .setPurchaseToken(purchaseToken)
            .build()

        return billingCall(BillingErrorCode.NETWORK) { respond ->
            client.acknowledgePurchase(params) { billingResult ->
                respond(billingResult.toResponse { Unit })
            }
        }
    }

    override suspend fun launchBillingFlow(
        activity: Activity,
        productId: String,
    ): BillingResponse<Unit> {
        val details = when (val queried = queryFreshProductDetails(productId)) {
            is BillingResponse.Error -> return queried
            is BillingResponse.Ok -> queried.value
                ?: return BillingResponse.Error(BillingErrorCode.DEVELOPER)
        }
        if (details.toProProduct() == null) {
            return BillingResponse.Error(BillingErrorCode.DEVELOPER)
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
        // A purchase counts as PURCHASED only if Play's signature verifies:
        // a forged/replayed purchase on a hooked billing service must never
        // unlock Pro.
        isPurchased = purchaseState == Purchase.PurchaseState.PURCHASED && isSignatureValid(),
        isPending = purchaseState == Purchase.PurchaseState.PENDING,
        isAcknowledged = isAcknowledged,
        purchaseToken = purchaseToken,
    )

    private fun ProductDetails.toProProduct(): ProProduct? {
        val price = oneTimePurchaseOfferDetails?.formattedPrice ?: return null
        return ProProduct(
            formattedPrice = price,
        )
    }

    private fun Int.toBillingErrorCode(): BillingErrorCode = when (this) {
        UnfetchedProduct.StatusCode.INVALID_PRODUCT_ID_FORMAT,
        UnfetchedProduct.StatusCode.PRODUCT_NOT_FOUND,
        UnfetchedProduct.StatusCode.NO_ELIGIBLE_OFFER,
        -> BillingErrorCode.DEVELOPER

        else -> BillingErrorCode.OTHER
    }

    private fun Purchase.isSignatureValid(): Boolean {
        val key = BuildConfig.PLAY_LICENSE_KEY
        if (key.isBlank()) {
            // No key configured: permissive in DEBUG only, so dev and CI builds
            // still function. A check that answers "valid" when it did not run
            // is not a check.
            if (BuildConfig.DEBUG) {
                Log.w("FastMaskBilling", "PLAY_LICENSE_KEY unset — purchase signature NOT verified")
            }
            return BuildConfig.DEBUG
        }
        return PurchaseSecurity.verify(key, originalJson, signature)
    }

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
