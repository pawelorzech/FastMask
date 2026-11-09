package com.fastmask.ui.auth

import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fastmask.R
import com.fastmask.domain.auth.MaskedEmailScopeMissingException
import com.fastmask.domain.auth.TokenFormat
import com.fastmask.domain.repository.SessionRepository
import com.fastmask.ui.common.UiErrors
import com.fastmask.ui.common.enterDemoOrErrorRes
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
    private val sessionRepository: SessionRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(LoginUiState())
    val uiState: StateFlow<LoginUiState> = _uiState.asStateFlow()

    private val _events = Channel<LoginEvent>(Channel.BUFFERED)
    val events: Flow<LoginEvent> = _events.receiveAsFlow()

    fun onTokenChange(token: String) {
        // Also receives platform/keyboard paste, not only keystrokes: bound it
        // so a huge paste cannot stall layout on the main thread.
        val boundedToken = TokenFormat.sanitizePasted(token)
        _uiState.update {
            it.copy(
                token = boundedToken,
                error = null,
                warningRes = null,
            )
        }
    }

    /**
     * Explicit "Paste" action, never automatic: reading the clipboard is a privacy signal
     * (Android 12+ surfaces a system toast). An empty clip or an image answers with a hint.
     */
    fun onTokenPasted(raw: String) {
        val pasted = TokenFormat.sanitizePasted(raw)
        if (pasted.isEmpty()) {
            // Deliberately leaves `token` alone: a whitespace-only clip must
            // not wipe a token the user typed by hand.
            _uiState.update { it.copy(error = null, warningRes = R.string.login_paste_empty) }
            return
        }
        _uiState.update {
            it.copy(
                token = pasted,
                error = null,
                warningRes = if (TokenFormat.shouldWarn(pasted)) R.string.login_token_warning_shape else null,
            )
        }
    }

    fun enterDemoMode() {
        if (_uiState.value.isLoading) return
        _uiState.update {
            it.copy(operation = LoginOperation.ENTER_DEMO, error = null)
        }
        viewModelScope.launch {
            val errorRes = sessionRepository.enterDemoOrErrorRes()
            if (errorRes == null) {
                _uiState.update {
                    it.copy(
                        token = "",
                        operation = null,
                        error = null,
                        warningRes = null,
                    )
                }
                _events.send(LoginEvent.EnterDemo)
            } else {
                _uiState.update { it.copy(operation = null, error = LoginError.Message(errorRes)) }
            }
        }
    }

    fun login() {
        if (_uiState.value.isLoading) return
        val token = TokenFormat.sanitize(_uiState.value.token)
        if (token.isBlank()) {
            _uiState.update {
                it.copy(
                    error = LoginError.Message(R.string.login_error_empty_token),
                    warningRes = null,
                )
            }
            return
        }

        _uiState.update {
            it.copy(
                operation = LoginOperation.LOGIN,
                error = null,
                warningRes = if (TokenFormat.shouldWarn(token)) R.string.login_token_warning_shape else null,
            )
        }
        viewModelScope.launch {
            sessionRepository.signIn(token).fold(
                onSuccess = {
                    _uiState.update { it.copy(token = "", operation = null) }
                    _events.send(LoginEvent.LoginSuccess)
                },
                onFailure = { error ->
                    if (error is MaskedEmailScopeMissingException) {
                        _uiState.update {
                            it.copy(
                                token = "",
                                operation = null,
                                error = LoginError.MissingScope,
                                warningRes = null,
                            )
                        }
                    } else {
                        // Keep the token on a retryable failure: the message says to try again,
                        // so wiping it would force a full re-paste.
                        val retryable = UiErrors.isRetryable(error)
                        _uiState.update {
                            it.copy(
                                token = if (retryable) it.token else "",
                                operation = null,
                                error = LoginError.Message(UiErrors.messageRes(error, R.string.login_error_failed)),
                                warningRes = if (retryable) it.warningRes else null,
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
    val operation: LoginOperation? = null,
    val error: LoginError? = null,
    /** Never prevents a login attempt: the token format is Fastmail's to change. */
    val warningRes: Int? = null,
) {
    val isLoading: Boolean get() = operation != null

    val errorRes: Int?
        get() = when (error) {
            null -> null
            is LoginError.Message -> error.res
            LoginError.MissingScope -> R.string.login_error_missing_masked_email_scope
        }

    val missingScope: Boolean get() = error == LoginError.MissingScope
}

sealed interface LoginError {
    data class Message(@StringRes val res: Int) : LoginError
    data object MissingScope : LoginError
}

enum class LoginOperation {
    LOGIN,
    ENTER_DEMO,
}

sealed class LoginEvent {
    data object LoginSuccess : LoginEvent()
    data object EnterDemo : LoginEvent()
}
