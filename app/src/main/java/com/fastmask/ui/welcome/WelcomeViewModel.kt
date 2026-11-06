package com.fastmask.ui.welcome

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fastmask.domain.repository.SessionRepository
import com.fastmask.ui.common.enterDemoOrErrorRes
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
class WelcomeViewModel @Inject constructor(
    private val sessionRepository: SessionRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(WelcomeUiState())
    val uiState: StateFlow<WelcomeUiState> = _uiState.asStateFlow()

    // Channel-backed one-time events: buffered delivery survives windows with
    // no active collector (e.g. mid-rotation) and each event is handled once.
    private val _events = Channel<WelcomeEvent>(Channel.BUFFERED)
    val events: Flow<WelcomeEvent> = _events.receiveAsFlow()

    fun enterDemoMode() {
        // Synchronous guard: two taps in one frame must not run two DataStore
        // writes and enqueue two navigation events.
        if (_uiState.value.isLoading) return
        _uiState.update { it.copy(isLoading = true, errorRes = null) }
        viewModelScope.launch {
            val errorRes = sessionRepository.enterDemoOrErrorRes()
            // On failure the user stays on Welcome with both CTAs released.
            _uiState.update { it.copy(isLoading = false, errorRes = errorRes) }
            if (errorRes == null) _events.send(WelcomeEvent.EnterDemo)
        }
    }
}

data class WelcomeUiState(
    val isLoading: Boolean = false,
    val errorRes: Int? = null,
)

sealed class WelcomeEvent {
    data object EnterDemo : WelcomeEvent()
}
