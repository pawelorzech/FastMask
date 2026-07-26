package com.fastmask.ui.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fastmask.R
import com.fastmask.domain.auth.MaskedEmailScopeMissingException
import com.fastmask.domain.auth.TokenFormat
import com.fastmask.domain.usecase.DemoModeActivator
import com.fastmask.domain.usecase.LoginUseCase
import com.fastmask.ui.common.UiErrors
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CoroutineExceptionHandler
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
    private val loginUseCase: LoginUseCase,
    private val demoModeActivator: DemoModeActivator,
) : ViewModel() {

    private val _uiState = MutableStateFlow(LoginUiState())
    val uiState: StateFlow<LoginUiState> = _uiState.asStateFlow()

    // Channel-backed one-time events: buffered delivery survives windows with
    // no active collector (e.g. mid-rotation) and each event is handled once.
    private val _events = Channel<LoginEvent>(Channel.BUFFERED)
    val events: Flow<LoginEvent> = _events.receiveAsFlow()

    private val writeErrorHandler = CoroutineExceptionHandler { _, _ -> }

    fun onTokenChange(token: String) {
        _uiState.update { it.copy(token = token, errorRes = null, warningRes = null) }
    }

    /**
     * Explicit "Paste" action. Never called automatically — reading the
     * clipboard is a privacy signal (Android 12+ surfaces a system toast), so
     * it happens only when the user asks for it.
     */
    fun onTokenPasted(raw: String) {
        _uiState.update {
            it.copy(
                token = TokenFormat.sanitize(raw),
                errorRes = null,
                warningRes = if (TokenFormat.shouldWarn(raw)) R.string.login_token_warning_shape else null,
            )
        }
    }

    /** Escape hatch for a user stuck on getting a token. */
    fun enterDemoMode() {
        viewModelScope.launch(writeErrorHandler) {
            demoModeActivator.activate()
            _events.send(LoginEvent.EnterDemo)
        }
    }

    fun login() {
        // Guard against rapid double-tap firing two login requests.
        if (_uiState.value.isLoading) return
        val token = TokenFormat.sanitize(_uiState.value.token)
        if (token.isBlank()) {
            _uiState.update { it.copy(errorRes = R.string.login_error_empty_token, warningRes = null) }
            return
        }

        // Set synchronously (before the coroutine is dispatched) so the
        // double-tap guard above cannot race the launch.
        _uiState.update {
            it.copy(
                isLoading = true,
                errorRes = null,
                warningRes = if (TokenFormat.shouldWarn(token)) R.string.login_token_warning_shape else null,
            )
        }
        viewModelScope.launch {
            loginUseCase(token).fold(
                onSuccess = {
                    // Drop the secret from UI state only once it is safely in
                    // encrypted storage. The shape hint is deliberately left
                    // standing: it describes what the user submitted, and the
                    // screen navigates away on success anyway — clearing it
                    // here would only matter if the login somehow stayed put.
                    _uiState.update { it.copy(token = "", isLoading = false) }
                    _events.send(LoginEvent.LoginSuccess)
                },
                onFailure = { error ->
                    if (error is MaskedEmailScopeMissingException) {
                        _uiState.update {
                            it.copy(
                                token = "",
                                isLoading = false,
                                errorRes = R.string.login_error_missing_masked_email_scope,
                            )
                        }
                    } else {
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
                }
            )
        }
    }
}

data class LoginUiState(
    val token: String = "",
    val isLoading: Boolean = false,
    val errorRes: Int? = null,
    /**
     * Soft, non-blocking hint that the field does not hold something shaped
     * like a Fastmail token. Distinct from [errorRes]: it never prevents a
     * login attempt, because the token format is Fastmail's to change.
     */
    val warningRes: Int? = null,
)

sealed class LoginEvent {
    data object LoginSuccess : LoginEvent()
    data object EnterDemo : LoginEvent()
}
