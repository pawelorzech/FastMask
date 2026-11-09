package com.fastmask.ui.create

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fastmask.R
import com.fastmask.domain.model.CreateMaskedEmailParams
import com.fastmask.domain.model.EmailState
import com.fastmask.domain.model.MaskPrefix
import com.fastmask.domain.repository.MaskedEmailRepository
import com.fastmask.ui.common.UiError
import com.fastmask.ui.common.UiErrors
import com.fastmask.ui.common.trimToNull
import com.fastmask.ui.navigation.NavRoutes
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
    savedStateHandle: SavedStateHandle,
    private val maskedEmailRepository: MaskedEmailRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(
        CreateMaskedEmailUiState(
            forDomain = savedStateHandle.get<String>(NavRoutes.ARG_FOR_DOMAIN)
                ?.takeIf { it.isNotBlank() }
                .orEmpty(),
        ),
    )
    val uiState: StateFlow<CreateMaskedEmailUiState> = _uiState.asStateFlow()

    private val _events = Channel<CreateMaskedEmailEvent>(Channel.BUFFERED)
    val events: Flow<CreateMaskedEmailEvent> = _events.receiveAsFlow()

    fun onPrefixChange(prefix: String) {
        val sanitized = MaskPrefix.sanitize(prefix)
        val errorRes = when (MaskPrefix.validate(sanitized)) {
            MaskPrefix.Error.TOO_LONG -> R.string.create_email_error_prefix_length
            MaskPrefix.Error.INVALID_CHARS -> R.string.create_email_error_prefix_chars
            null -> null
        }
        _uiState.update { it.copy(emailPrefix = sanitized.take(MaskPrefix.MAX_LENGTH), prefixErrorRes = errorRes) }
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
        if (state.prefixErrorRes != null) return
        // The button's `enabled` flips only on the next recomposition, so two taps in
        // one frame would otherwise create two real masks.
        if (state.isLoading) return

        _uiState.update { it.copy(isLoading = true, error = null) }
        viewModelScope.launch {
            // An empty field means "not set", not "clear".
            val params = CreateMaskedEmailParams(
                state = state.initialState,
                forDomain = state.forDomain.trimToNull(),
                description = state.description.trimToNull(),
                emailPrefix = state.emailPrefix.trimToNull(),
                url = state.url.trimToNull(),
            )

            maskedEmailRepository.createMaskedEmail(params).fold(
                onSuccess = { email ->
                    _uiState.update { it.copy(isLoading = false) }
                    _events.send(CreateMaskedEmailEvent.Created(email.email))
                },
                onFailure = { error ->
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            error = UiErrors.of(error, R.string.create_email_error_failed)
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
    val error: UiError? = null,
    val prefixErrorRes: Int? = null,
) {
    val isDirty: Boolean
        get() = emailPrefix.isNotEmpty() || forDomain.isNotEmpty() ||
            description.isNotEmpty() || url.isNotEmpty()
}

sealed class CreateMaskedEmailEvent {
    data class Created(val email: String) : CreateMaskedEmailEvent()
}
