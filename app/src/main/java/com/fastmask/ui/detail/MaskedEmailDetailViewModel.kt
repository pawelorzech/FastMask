package com.fastmask.ui.detail

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fastmask.domain.model.EmailState
import com.fastmask.domain.model.MaskedEmail
import com.fastmask.domain.model.UpdateMaskedEmailParams
import com.fastmask.domain.usecase.DeleteMaskedEmailUseCase
import com.fastmask.domain.usecase.GetMaskedEmailsUseCase
import com.fastmask.domain.usecase.UpdateMaskedEmailUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class MaskedEmailDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val getMaskedEmailsUseCase: GetMaskedEmailsUseCase,
    private val updateMaskedEmailUseCase: UpdateMaskedEmailUseCase,
    private val deleteMaskedEmailUseCase: DeleteMaskedEmailUseCase
) : ViewModel() {

    private val emailId: String = savedStateHandle.get<String>("emailId")
        ?: throw IllegalArgumentException("emailId is required")

    private val _uiState = MutableStateFlow(MaskedEmailDetailUiState())
    val uiState: StateFlow<MaskedEmailDetailUiState> = _uiState.asStateFlow()

    private val _events = MutableSharedFlow<MaskedEmailDetailEvent>()
    val events: SharedFlow<MaskedEmailDetailEvent> = _events.asSharedFlow()

    init {
        loadEmail()
    }

    fun loadEmail() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }

            getMaskedEmailsUseCase().fold(
                onSuccess = { emails ->
                    val email = emails.find { it.id == emailId }
                    if (email != null) {
                        _uiState.update {
                            it.copy(
                                isLoading = false,
                                email = email,
                                editedDescription = email.description ?: "",
                                editedForDomain = email.forDomain ?: "",
                                editedUrl = email.url ?: ""
                            )
                        }
                    } else {
                        _uiState.update {
                            it.copy(
                                isLoading = false,
                                error = "Email not found"
                            )
                        }
                    }
                },
                onFailure = { error ->
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            error = error.message ?: "Failed to load email"
                        )
                    }
                }
            )
        }
    }

    fun onDescriptionChange(description: String) {
        _uiState.update { it.copy(editedDescription = description) }
    }

    fun onForDomainChange(domain: String) {
        _uiState.update { it.copy(editedForDomain = domain) }
    }

    fun onUrlChange(url: String) {
        _uiState.update { it.copy(editedUrl = url) }
    }

    fun toggleState() {
        val email = _uiState.value.email ?: return
        val newState = if (email.state == EmailState.ENABLED) EmailState.DISABLED else EmailState.ENABLED
        updateState(newState)
    }

    fun enable() = updateState(EmailState.ENABLED)

    fun disable() = updateState(EmailState.DISABLED)

    private fun updateState(newState: EmailState) {
        viewModelScope.launch {
            _uiState.update { it.copy(isUpdating = true) }

            updateMaskedEmailUseCase(emailId, UpdateMaskedEmailParams(state = newState)).fold(
                onSuccess = {
                    loadEmail()
                    _events.emit(MaskedEmailDetailEvent.Updated)
                },
                onFailure = { error ->
                    _uiState.update {
                        it.copy(
                            isUpdating = false,
                            error = error.message ?: "Failed to update"
                        )
                    }
                }
            )
        }
    }

    fun saveChanges() {
        val state = _uiState.value
        val email = state.email ?: return

        val hasChanges = state.editedDescription != (email.description ?: "") ||
                state.editedForDomain != (email.forDomain ?: "") ||
                state.editedUrl != (email.url ?: "")

        if (!hasChanges) return

        viewModelScope.launch {
            _uiState.update { it.copy(isUpdating = true) }

            val params = UpdateMaskedEmailParams(
                description = state.editedDescription.takeIf { it.isNotBlank() },
                forDomain = state.editedForDomain.takeIf { it.isNotBlank() },
                url = state.editedUrl.takeIf { it.isNotBlank() }
            )

            updateMaskedEmailUseCase(emailId, params).fold(
                onSuccess = {
                    loadEmail()
                    _events.emit(MaskedEmailDetailEvent.Updated)
                },
                onFailure = { error ->
                    _uiState.update {
                        it.copy(
                            isUpdating = false,
                            error = error.message ?: "Failed to save changes"
                        )
                    }
                }
            )
        }
    }

    fun delete() {
        viewModelScope.launch {
            _uiState.update { it.copy(isDeleting = true) }

            deleteMaskedEmailUseCase(emailId).fold(
                onSuccess = {
                    _events.emit(MaskedEmailDetailEvent.Deleted)
                },
                onFailure = { error ->
                    _uiState.update {
                        it.copy(
                            isDeleting = false,
                            error = error.message ?: "Failed to delete"
                        )
                    }
                }
            )
        }
    }
}

data class MaskedEmailDetailUiState(
    val isLoading: Boolean = false,
    val isUpdating: Boolean = false,
    val isDeleting: Boolean = false,
    val email: MaskedEmail? = null,
    val editedDescription: String = "",
    val editedForDomain: String = "",
    val editedUrl: String = "",
    val error: String? = null
)

sealed class MaskedEmailDetailEvent {
    data object Updated : MaskedEmailDetailEvent()
    data object Deleted : MaskedEmailDetailEvent()
}
