package com.fastmask.ui.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fastmask.R
import com.fastmask.domain.usecase.LoginUseCase
import com.fastmask.ui.common.UiErrors
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class LoginViewModel @Inject constructor(
    private val loginUseCase: LoginUseCase
) : ViewModel() {

    private val _uiState = MutableStateFlow(LoginUiState())
    val uiState: StateFlow<LoginUiState> = _uiState.asStateFlow()

    // Channel-backed one-time events: buffered delivery survives windows with
    // no active collector (e.g. mid-rotation) and each event is handled once.
    private val _events = Channel<LoginEvent>(Channel.BUFFERED)
    val events: Flow<LoginEvent> = _events.receiveAsFlow()

    fun onTokenChange(token: String) {
        _uiState.update { it.copy(token = token, errorRes = null) }
    }

    fun login() {
        // Guard against rapid double-tap firing two login requests.
        if (_uiState.value.isLoading) return
        // Remove all whitespace characters (spaces, newlines, tabs) from the token
        val token = _uiState.value.token.filterNot { it.isWhitespace() }
        if (token.isBlank()) {
            _uiState.update { it.copy(errorRes = R.string.login_error_empty_token) }
            return
        }

        // Set synchronously (before the coroutine is dispatched) so the
        // double-tap guard above cannot race the launch.
        _uiState.update { it.copy(isLoading = true, errorRes = null) }
        viewModelScope.launch {
            loginUseCase(token).fold(
                onSuccess = {
                    // Drop the secret from UI state only once it is safely in
                    // encrypted storage.
                    _uiState.update { it.copy(token = "", isLoading = false) }
                    _events.send(LoginEvent.LoginSuccess)
                },
                onFailure = { error ->
                    // Token hygiene is kept where it has value — after the
                    // token has been accepted, or definitively rejected — but
                    // NOT on a retryable failure. UiErrors maps no-network /
                    // 429 / 5xx to messages that literally tell the user to
                    // try again; wiping a masked ~40-character token at the
                    // same moment contradicts that instruction and forces a
                    // full re-paste to press the button a second time.
                    val retryable = UiErrors.isRetryable(error)
                    _uiState.update {
                        it.copy(
                            token = if (retryable) it.token else "",
                            isLoading = false,
                            errorRes = UiErrors.messageRes(error, R.string.login_error_failed),
                        )
                    }
                }
            )
        }
    }
}

data class LoginUiState(
    val token: String = "",
    val isLoading: Boolean = false,
    val errorRes: Int? = null
)

sealed class LoginEvent {
    data object LoginSuccess : LoginEvent()
}
