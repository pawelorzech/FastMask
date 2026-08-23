package com.fastmask.ui.welcome

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fastmask.R
import com.fastmask.domain.usecase.DemoModeActivator
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
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
class WelcomeViewModel @Inject constructor(
    private val demoModeActivator: DemoModeActivator,
) : ViewModel() {

    private val _uiState = MutableStateFlow(WelcomeUiState())
    val uiState: StateFlow<WelcomeUiState> = _uiState.asStateFlow()

    // Channel-backed one-time events: buffered delivery survives windows with
    // no active collector (e.g. mid-rotation) and each event is handled once.
    private val _events = Channel<WelcomeEvent>(Channel.BUFFERED)
    val events: Flow<WelcomeEvent> = _events.receiveAsFlow()

    /**
     * Enter demo mode. The sequence itself lives in [DemoModeActivator] so the
     * login screen's demo exit runs it too, instead of a second copy.
     */
    fun enterDemoMode() {
        // Synchronous guard: two taps in one frame must not run two DataStore
        // writes and enqueue two navigation events.
        if (_uiState.value.isLoading) return
        _uiState.update { it.copy(isLoading = true, errorRes = null) }
        viewModelScope.launch {
            try {
                demoModeActivator.activate()
                _uiState.update { it.copy(isLoading = false) }
                _events.send(WelcomeEvent.EnterDemo)
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (_: Throwable) {
                // A dead button is indistinguishable from a broken app. Keep
                // the user on Welcome, release both CTAs and explain how to
                // recover from the likely local-storage failure.
                _uiState.update {
                    it.copy(isLoading = false, errorRes = R.string.demo_error_start)
                }
            }
        }
    }

    fun goToSignIn() {
        if (_uiState.value.isLoading) return
        viewModelScope.launch {
            _events.send(WelcomeEvent.GoToSignIn)
        }
    }
}

data class WelcomeUiState(
    val isLoading: Boolean = false,
    val errorRes: Int? = null,
)

sealed class WelcomeEvent {
    data object EnterDemo : WelcomeEvent()
    data object GoToSignIn : WelcomeEvent()
}
