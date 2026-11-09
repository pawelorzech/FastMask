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

    private val source: String = savedStateHandle[NavRoutes.ARG_SOURCE] ?: PRO_SOURCE_UNKNOWN

    private val _uiState = MutableStateFlow(ProUiState())
    val uiState: StateFlow<ProUiState> = _uiState.asStateFlow()

    private val _events = Channel<ProUiEvent>(Channel.BUFFERED)
    val events: Flow<ProUiEvent> = _events.receiveAsFlow()

    init {
        analytics.track(MonetizationEvent.PAYWALL_VIEWED, source = source)
        proRepository.drainPendingEvents()
        viewModelScope.launch {
            proRepository.proStatus.collect { status ->
                _uiState.update { it.copy(status = status) }
            }
        }
        viewModelScope.launch {
            proRepository.events.collect { event ->
                // Only a purchase that completes while this screen is open is a celebration.
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
        if (_uiState.value.purchaseInFlight) return
        _uiState.update { it.copy(purchaseInFlight = true) }
        viewModelScope.launch {
            when (proRepository.purchase(activity)) {
                PurchaseLaunch.LAUNCHED -> Unit
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

    // onCleared, so gesture/system back counts too, not just the close button.
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
    val celebrate: Boolean = false,
) {
    val purchaseArea: PurchaseArea
        get() = when {
            status == ProStatus.PRO -> PurchaseArea.OWNED
            status == ProStatus.PENDING -> PurchaseArea.PENDING
            productState == ProductState.LOADING -> PurchaseArea.LOADING
            productState == ProductState.UNAVAILABLE -> PurchaseArea.UNAVAILABLE
            else -> PurchaseArea.READY
        }
}

enum class PurchaseArea { OWNED, PENDING, LOADING, UNAVAILABLE, READY }

enum class ProductState { LOADING, READY, UNAVAILABLE }

enum class ProHaptic { NONE, CONFIRM, REJECT }

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
