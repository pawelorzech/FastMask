package com.fastmask.ui.pro

import android.app.Activity
import androidx.annotation.StringRes
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fastmask.R
import com.fastmask.domain.analytics.MonetizationAnalytics
import com.fastmask.domain.analytics.MonetizationEvent
import com.fastmask.domain.model.ProStatus
import com.fastmask.domain.repository.ProProduct
import com.fastmask.domain.repository.ProPurchaseEvent
import com.fastmask.domain.repository.ProRepository
import com.fastmask.domain.repository.PurchaseLaunch
import com.fastmask.domain.repository.RestoreResult
import com.fastmask.ui.navigation.NavRoutes
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.async
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ProViewModel @Inject constructor(
    private val proRepository: ProRepository,
    private val analytics: MonetizationAnalytics,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val source: String = savedStateHandle[NavRoutes.ARG_SOURCE] ?: "unknown"

    private val _uiState = MutableStateFlow(ProUiState())
    val uiState: StateFlow<ProUiState> = _uiState.asStateFlow()

    private val _events = Channel<ProUiEvent>(Channel.BUFFERED)
    val events: Flow<ProUiEvent> = _events.receiveAsFlow()

    init {
        analytics.track(MonetizationEvent.PAYWALL_VIEWED, source = source)
        // Events buffered while no paywall was open (e.g. a purchase resolved
        // after the user backed out) are stale by now — state is in proStatus.
        proRepository.drainPendingEvents()
        viewModelScope.launch {
            proRepository.proStatus.collect { status ->
                _uiState.update { it.copy(status = status) }
            }
        }
        viewModelScope.launch {
            proRepository.events.collect { event ->
                // Only a purchase that completes while this screen is open is a
                // celebration; an owner whose entitlement merely loads is not.
                _uiState.update {
                    it.copy(
                        purchaseInFlight = false,
                        celebrate = it.celebrate || event is ProPurchaseEvent.Completed,
                    )
                }
                _events.send(
                    when (event) {
                        is ProPurchaseEvent.Completed -> ProUiEvent.PurchaseCompleted
                        is ProPurchaseEvent.Pending -> ProUiEvent.PurchasePending
                        is ProPurchaseEvent.Cancelled -> ProUiEvent.PurchaseCancelled
                        is ProPurchaseEvent.Failed -> ProUiEvent.PurchaseFailed
                    }
                )
            }
        }
        loadProduct()
    }

    fun loadProduct() {
        _uiState.update { it.copy(productState = ProductState.LOADING) }
        viewModelScope.launch {
            val product = coroutineScope {
                val productDeferred = async { proRepository.getProduct() }
                proRepository.refresh()
                productDeferred.await()
            }
            _uiState.update {
                it.copy(
                    product = product,
                    productState = if (product == null) ProductState.UNAVAILABLE else ProductState.READY,
                )
            }
        }
    }

    fun buy(activity: Activity) {
        // Guard set synchronously BEFORE launching the coroutine — a deferred
        // flag lets a rapid double-tap start two billing flows (see the
        // double-tap C/R/L in the 1.6.0 audit).
        if (_uiState.value.purchaseInFlight) return
        _uiState.update { it.copy(purchaseInFlight = true) }
        viewModelScope.launch {
            when (proRepository.purchase(activity)) {
                PurchaseLaunch.LAUNCHED -> Unit // outcome arrives via events
                PurchaseLaunch.ALREADY_OWNED -> {
                    _uiState.update { it.copy(purchaseInFlight = false) }
                    _events.send(ProUiEvent.PurchaseCompleted)
                }
                PurchaseLaunch.UNAVAILABLE -> {
                    _uiState.update { it.copy(purchaseInFlight = false) }
                    _events.send(ProUiEvent.BillingUnavailable)
                }
                PurchaseLaunch.ERROR -> {
                    _uiState.update { it.copy(purchaseInFlight = false) }
                    _events.send(ProUiEvent.PurchaseFailed)
                }
            }
        }
    }

    fun restore() {
        if (_uiState.value.restoreInFlight) return
        _uiState.update { it.copy(restoreInFlight = true) }
        viewModelScope.launch {
            val result = proRepository.restore()
            _uiState.update { it.copy(restoreInFlight = false) }
            _events.send(
                when (result) {
                    RestoreResult.RESTORED -> ProUiEvent.Restored
                    RestoreResult.NOTHING_TO_RESTORE -> ProUiEvent.NothingToRestore
                    RestoreResult.UNAVAILABLE -> ProUiEvent.BillingUnavailable
                    RestoreResult.ERROR -> ProUiEvent.RestoreFailed
                }
            )
        }
    }

    // onCleared, not the back button's onClick: most paywall dismissals are
    // gesture/system back, which never touches the button — tracking there
    // undercounted nearly all closes.
    override fun onCleared() {
        analytics.track(MonetizationEvent.PAYWALL_CLOSED, source = source)
        super.onCleared()
    }
}

data class ProUiState(
    val status: ProStatus = ProStatus.FREE,
    val product: ProProduct? = null,
    val productState: ProductState = ProductState.LOADING,
    val purchaseInFlight: Boolean = false,
    val restoreInFlight: Boolean = false,
    /** True only for a purchase completed while this screen was open. */
    val celebrate: Boolean = false,
)

enum class ProductState { LOADING, READY, UNAVAILABLE }

/** Haptic that accompanies a paywall snackbar. */
enum class ProHaptic { NONE, CONFIRM, REJECT }

/** One-shot paywall outcome: the snackbar [message] and the [haptic] that goes with it. */
sealed class ProUiEvent(@StringRes val message: Int, val haptic: ProHaptic = ProHaptic.NONE) {
    data object PurchaseCompleted : ProUiEvent(R.string.pro_purchase_success, ProHaptic.CONFIRM)
    data object PurchasePending : ProUiEvent(R.string.pro_purchase_pending)
    data object PurchaseCancelled : ProUiEvent(R.string.pro_purchase_cancelled)
    data object PurchaseFailed : ProUiEvent(R.string.pro_purchase_failed, ProHaptic.REJECT)
    data object BillingUnavailable : ProUiEvent(R.string.pro_billing_unavailable, ProHaptic.REJECT)
    data object Restored : ProUiEvent(R.string.pro_restore_success, ProHaptic.CONFIRM)
    data object NothingToRestore : ProUiEvent(R.string.pro_restore_nothing)
    data object RestoreFailed : ProUiEvent(R.string.pro_restore_failed, ProHaptic.REJECT)
}
