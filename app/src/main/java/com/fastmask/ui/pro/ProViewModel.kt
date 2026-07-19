package com.fastmask.ui.pro

import android.app.Activity
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fastmask.domain.analytics.MonetizationAnalytics
import com.fastmask.domain.analytics.MonetizationEvent
import com.fastmask.domain.model.ProStatus
import com.fastmask.domain.repository.ProProduct
import com.fastmask.domain.repository.ProPurchaseEvent
import com.fastmask.domain.repository.ProRepository
import com.fastmask.domain.repository.PurchaseLaunch
import com.fastmask.domain.repository.RestoreResult
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
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

    private val source: String = savedStateHandle["source"] ?: "unknown"

    private val _uiState = MutableStateFlow(ProUiState())
    val uiState: StateFlow<ProUiState> = _uiState.asStateFlow()

    private val _events = Channel<ProUiEvent>(Channel.BUFFERED)
    val events: Flow<ProUiEvent> = _events.receiveAsFlow()

    init {
        analytics.track(MonetizationEvent.PAYWALL_VIEWED, source = source)
        viewModelScope.launch {
            proRepository.proStatus.collect { status ->
                _uiState.update { it.copy(status = status) }
            }
        }
        viewModelScope.launch {
            proRepository.events.collect { event ->
                _uiState.update { it.copy(purchaseInFlight = false) }
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
            proRepository.refresh()
            val product = proRepository.getProduct()
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

    fun onClosed() {
        analytics.track(MonetizationEvent.PAYWALL_CLOSED, source = source)
    }
}

data class ProUiState(
    val status: ProStatus = ProStatus.FREE,
    val product: ProProduct? = null,
    val productState: ProductState = ProductState.LOADING,
    val purchaseInFlight: Boolean = false,
    val restoreInFlight: Boolean = false,
)

enum class ProductState { LOADING, READY, UNAVAILABLE }

sealed class ProUiEvent {
    data object PurchaseCompleted : ProUiEvent()
    data object PurchasePending : ProUiEvent()
    data object PurchaseCancelled : ProUiEvent()
    data object PurchaseFailed : ProUiEvent()
    data object BillingUnavailable : ProUiEvent()
    data object Restored : ProUiEvent()
    data object NothingToRestore : ProUiEvent()
    data object RestoreFailed : ProUiEvent()
}
