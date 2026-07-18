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
            val outcome = loginUseCase(token)
            _uiState.update { it.copy(token = "", isLoading = false) }

            outcome.fold(
                onSuccess = {
                    _events.send(LoginEvent.LoginSuccess)
                },
                onFailure = { error ->
                    _uiState.update {
                        it.copy(errorRes = UiErrors.messageRes(error, R.string.login_error_failed))
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
