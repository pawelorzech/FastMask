package com.fastmask.ui.detail

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fastmask.R
import com.fastmask.domain.model.EmailState
import com.fastmask.domain.model.MaskedEmail
import com.fastmask.domain.model.UpdateMaskedEmailParams
import com.fastmask.domain.usecase.DeleteMaskedEmailUseCase
import com.fastmask.domain.usecase.GetMaskedEmailsUseCase
import com.fastmask.domain.usecase.UpdateMaskedEmailUseCase
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

    // Channel-backed one-time events: buffered delivery survives windows with
    // no active collector (e.g. mid-rotation) and each event is handled once.
    private val _events = Channel<MaskedEmailDetailEvent>(Channel.BUFFERED)
    val events: Flow<MaskedEmailDetailEvent> = _events.receiveAsFlow()

    init {
        loadEmail()
    }

    /**
     * @param resetEdits when true (initial load / retry) the editable fields are
     *   (re)seeded from the server value. The reload triggered AFTER a save or a
     *   state toggle passes false: overwriting `edited*` there would drop any
     *   keystrokes the user typed while the network call was in flight.
     */
    fun loadEmail(resetEdits: Boolean = true) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorRes = null) }

            getMaskedEmailsUseCase().fold(
                onSuccess = { emails ->
                    val email = emails.find { it.id == emailId }
                    if (email != null) {
                        _uiState.update {
                            it.copy(
                                isLoading = false,
                                isUpdating = false,
                                email = email,
                                editedDescription = if (resetEdits) email.description ?: "" else it.editedDescription,
                                editedForDomain = if (resetEdits) email.forDomain ?: "" else it.editedForDomain,
                                editedUrl = if (resetEdits) email.url ?: "" else it.editedUrl
                            )
                        }
                    } else {
                        _uiState.update {
                            it.copy(
                                isLoading = false,
                                isUpdating = false,
                                errorRes = R.string.email_detail_error_load
                            )
                        }
                    }
                },
                onFailure = { error ->
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            isUpdating = false,
                            errorRes = UiErrors.messageRes(error, R.string.email_detail_error_load)
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
        if (_uiState.value.isUpdating || _uiState.value.isDeleting) return
        // Set synchronously so the guard above cannot race the launch.
        _uiState.update { it.copy(isUpdating = true) }
        viewModelScope.launch {

            updateMaskedEmailUseCase(emailId, UpdateMaskedEmailParams(state = newState)).fold(
                onSuccess = {
                    loadEmail(resetEdits = false)
                    _events.send(MaskedEmailDetailEvent.Updated)
                },
                onFailure = { error ->
                    _uiState.update {
                        it.copy(
                            isUpdating = false,
                            errorRes = UiErrors.messageRes(error, R.string.email_detail_error_update)
                        )
                    }
                }
            )
        }
    }

    fun saveChanges() {
        val state = _uiState.value
        val email = state.email ?: return
        if (state.isUpdating || state.isDeleting) return

        // Send only the fields that actually changed. A field cleared by the user
        // is sent as "" (which clears it server-side) — `null` means "not changed"
        // and is omitted from the JMAP update entirely. Previously a cleared field
        // was mapped to null, so the server kept the old value and the UI silently
        // reverted the user's deletion.
        val params = UpdateMaskedEmailParams(
            description = state.editedDescription.trim().takeIf { it != (email.description ?: "") },
            forDomain = state.editedForDomain.trim().takeIf { it != (email.forDomain ?: "") },
            url = state.editedUrl.trim().takeIf { it != (email.url ?: "") }
        )

        val hasChanges = params.description != null || params.forDomain != null || params.url != null
        if (!hasChanges) return

        // Set synchronously so the guard above cannot race the launch.
        _uiState.update { it.copy(isUpdating = true) }
        viewModelScope.launch {

            updateMaskedEmailUseCase(emailId, params).fold(
                onSuccess = {
                    loadEmail(resetEdits = false)
                    _events.send(MaskedEmailDetailEvent.Updated)
                },
                onFailure = { error ->
                    _uiState.update {
                        it.copy(
                            isUpdating = false,
                            errorRes = UiErrors.messageRes(error, R.string.email_detail_error_save)
                        )
                    }
                }
            )
        }
    }

    fun delete() {
        if (_uiState.value.isDeleting || _uiState.value.isUpdating) return
        // Set synchronously so the guard above cannot race the launch.
        _uiState.update { it.copy(isDeleting = true) }
        viewModelScope.launch {

            deleteMaskedEmailUseCase(emailId).fold(
                onSuccess = {
                    // Carry the pre-archive state so the list's Undo can put
                    // the mask back EXACTLY as it was — a DISABLED mask must
                    // not come back accepting mail.
                    _events.send(
                        MaskedEmailDetailEvent.Deleted(
                            id = emailId,
                            previousState = _uiState.value.email?.state ?: EmailState.ENABLED,
                        )
                    )
                },
                onFailure = { error ->
                    _uiState.update {
                        it.copy(
                            isDeleting = false,
                            errorRes = UiErrors.messageRes(error, R.string.email_detail_error_delete)
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
    val errorRes: Int? = null
)

sealed class MaskedEmailDetailEvent {
    data object Updated : MaskedEmailDetailEvent()
    data class Deleted(val id: String, val previousState: EmailState) : MaskedEmailDetailEvent()
}
