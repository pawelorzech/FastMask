package com.fastmask.ui.create

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fastmask.R
import com.fastmask.domain.model.CreateMaskedEmailParams
import com.fastmask.domain.model.EmailState
import com.fastmask.domain.usecase.CreateMaskedEmailUseCase
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
class CreateMaskedEmailViewModel @Inject constructor(
    private val createMaskedEmailUseCase: CreateMaskedEmailUseCase
) : ViewModel() {

    private val _uiState = MutableStateFlow(CreateMaskedEmailUiState())
    val uiState: StateFlow<CreateMaskedEmailUiState> = _uiState.asStateFlow()

    // Channel-backed one-time events: buffered delivery survives windows with
    // no active collector (e.g. mid-rotation) and each event is handled once.
    private val _events = Channel<CreateMaskedEmailEvent>(Channel.BUFFERED)
    val events: Flow<CreateMaskedEmailEvent> = _events.receiveAsFlow()

    fun onPrefixChange(prefix: String) {
        val sanitized = prefix.lowercase().filter { it.isLetterOrDigit() || it == '_' }
        val error = when {
            sanitized.length > 64 -> "Prefix must be 64 characters or less"
            sanitized.isNotEmpty() && !sanitized.matches(Regex("^[a-z0-9_]*$")) ->
                "Only lowercase letters, numbers, and underscores allowed"
            else -> null
        }
        _uiState.update { it.copy(emailPrefix = sanitized.take(64), prefixError = error) }
    }

    fun onDomainChange(domain: String) {
        _uiState.update { it.copy(forDomain = domain) }
    }

    fun onDescriptionChange(description: String) {
        _uiState.update { it.copy(description = description) }
    }

    fun onUrlChange(url: String) {
        _uiState.update { it.copy(url = url) }
    }

    fun onStateChange(state: EmailState) {
        _uiState.update { it.copy(initialState = state) }
    }

    fun create() {
        val state = _uiState.value
        if (state.prefixError != null) return
        // Guard against rapid double-tap: the button's `enabled` flips only on the
        // next recomposition, so two taps in one frame would otherwise both land
        // here and create two real masks on the Fastmail account.
        if (state.isLoading) return

        // Set synchronously (before the coroutine is dispatched) so the
        // double-tap guard above cannot race the launch.
        _uiState.update { it.copy(isLoading = true, errorRes = null) }
        viewModelScope.launch {
            val params = CreateMaskedEmailParams(
                state = state.initialState,
                forDomain = state.forDomain.takeIf { it.isNotBlank() },
                description = state.description.takeIf { it.isNotBlank() },
                emailPrefix = state.emailPrefix.takeIf { it.isNotBlank() },
                url = state.url.takeIf { it.isNotBlank() }
            )

            createMaskedEmailUseCase(params).fold(
                onSuccess = { email ->
                    _uiState.update { it.copy(isLoading = false) }
                    _events.send(CreateMaskedEmailEvent.Created(email.email))
                },
                onFailure = { error ->
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            errorRes = UiErrors.messageRes(error, R.string.create_email_error_failed)
                        )
                    }
                }
            )
        }
    }
}

data class CreateMaskedEmailUiState(
    val emailPrefix: String = "",
    val forDomain: String = "",
    val description: String = "",
    val url: String = "",
    val initialState: EmailState = EmailState.ENABLED,
    val isLoading: Boolean = false,
    val errorRes: Int? = null,
    val prefixError: String? = null
)

sealed class CreateMaskedEmailEvent {
    data class Created(val email: String) : CreateMaskedEmailEvent()
}
