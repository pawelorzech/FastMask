package com.fastmask.ui.detail

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fastmask.R
import com.fastmask.domain.model.EmailState
import com.fastmask.domain.model.MaskedEmail
import com.fastmask.domain.model.UpdateMaskedEmailParams
import com.fastmask.domain.repository.MaskedEmailRepository
import com.fastmask.ui.common.UiError
import com.fastmask.ui.common.UiErrorAction
import com.fastmask.ui.common.UiErrors
import com.fastmask.ui.common.trimmedIfChangedFrom
import com.fastmask.core.SingleFlight
import com.fastmask.ui.navigation.NavRoutes
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class MaskedEmailDetailViewModel @Inject constructor(
    private val savedStateHandle: SavedStateHandle,
    private val maskedEmailRepository: MaskedEmailRepository,
) : ViewModel() {

    val emailId: String = requireNotNull(savedStateHandle.get<String>(NavRoutes.ARG_EMAIL_ID)) {
        "emailId is required"
    }

    private val _uiState = MutableStateFlow(
        MaskedEmailDetailUiState(isEditing = savedStateHandle[KEY_EDITING] ?: false),
    )
    val uiState: StateFlow<MaskedEmailDetailUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch(Dispatchers.Unconfined) {
            _uiState.map { it.isEditing }.distinctUntilChanged().collect { savedStateHandle[KEY_EDITING] = it }
        }
    }

    private val _events = Channel<MaskedEmailDetailEvent>(Channel.BUFFERED)
    val events: Flow<MaskedEmailDetailEvent> = _events.receiveAsFlow()

    private val loadFlight = SingleFlight<Boolean>(
        scope = viewModelScope,
        merge = { queued, new -> queued && new },
        start = { resetEdits ->
            // A failed refresh with a mask already on screen (cache seed or an earlier
            // success) degrades to the inline error above Save: the full-screen error
            // state would throw away what the user is looking at.
            val hadEmail = _uiState.value.email != null
            if (!hadEmail) {
                _uiState.update { it.copy(isLoading = true, error = null) }
            }
            suspend { loadOnce(resetEdits, hadEmail) }
        },
    )

    init {
        loadEmail()
    }

    // resetEdits is false for the reload after a save or toggle: overwriting edited*
    // there would drop keystrokes typed while the call was in flight.
    fun loadEmail(resetEdits: Boolean = true) = loadFlight.request(resetEdits)

    private suspend fun loadOnce(resetEdits: Boolean, hadEmail: Boolean) {
        if (!hadEmail && _uiState.value.email == null) {
            seedFromCache(resetEdits)
        }

        maskedEmailRepository.getMaskedEmail(emailId).fold(
            onSuccess = { email ->
                if (email != null) {
                    applyLoadedEmail(email, resetEdits)
                } else {
                    // Server does not know this id: deleted elsewhere,
                    // or a stale deep link. Full-screen only when nothing
                    // is on screen yet.
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            updateOperation = null,
                            error = UiError(R.string.email_detail_error_load, UiErrorAction.RETRY)
                        )
                    }
                }
            },
            onFailure = { error ->
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        updateOperation = null,
                        error = UiErrors.of(error, R.string.email_detail_error_load, UiErrorAction.RETRY)
                    )
                }
            }
        )
    }

    private suspend fun seedFromCache(resetEdits: Boolean) {
        val cached = maskedEmailRepository.cachedMaskedEmails()?.masks?.find { it.id == emailId } ?: return
        if (_uiState.value.email != null) return
        applyLoadedEmail(cached, resetEdits)
    }

    private fun applyLoadedEmail(email: MaskedEmail, resetEdits: Boolean) {
        _uiState.update {
            fun refreshedField(draft: String, previous: String?, fresh: String?): String =
                if (resetEdits && draft == previous.orEmpty()) fresh.orEmpty() else draft

            val loaded = it.copy(
                isLoading = false,
                updateOperation = null,
                email = email,
                editedDescription = refreshedField(it.editedDescription, it.email?.description, email.description),
                editedForDomain = refreshedField(it.editedForDomain, it.email?.forDomain, email.forDomain),
                editedUrl = refreshedField(it.editedUrl, it.email?.url, email.url)
            )
            // A save lands when the reload after it does: only then does the
            // draft match the server again and edit mode can close. A failed
            // save, or keystrokes typed while it was in flight, keep the draft
            // and stay in edit mode.
            if (it.updateOperation == MaskedEmailDetailUpdate.SAVE_CHANGES && !loaded.hasChanges) {
                loaded.copy(isEditing = false)
            } else {
                loaded
            }
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

    fun startEditing() {
        _uiState.update { it.copy(isEditing = true) }
    }

    fun discardEdits() {
        _uiState.update {
            val email = it.email
            it.copy(
                isEditing = false,
                editedDescription = email?.description.orEmpty(),
                editedForDomain = email?.forDomain.orEmpty(),
                editedUrl = email?.url.orEmpty(),
            )
        }
    }

    fun enable() = updateState(EmailState.ENABLED)

    fun disable() = updateState(EmailState.DISABLED)

    private fun updateState(newState: EmailState) {
        if (_uiState.value.isUpdating || _uiState.value.isDeleting) return
        _uiState.update { it.copy(updateOperation = MaskedEmailDetailUpdate.TOGGLE_STATE) }
        viewModelScope.launch {
            maskedEmailRepository.updateMaskedEmail(emailId, UpdateMaskedEmailParams(state = newState)).fold(
                onSuccess = {
                    loadEmail(resetEdits = false)
                    _events.send(MaskedEmailDetailEvent.Updated)
                },
                onFailure = { error ->
                    _uiState.update {
                        it.copy(
                            updateOperation = null,
                            error = UiErrors.of(error, R.string.error_update_mask).withoutRetry()
                        )
                    }
                }
            )
        }
    }

    fun saveChanges() {
        val state = _uiState.value
        if (state.isUpdating || state.isDeleting) return
        val params = state.pendingChanges ?: return

        _uiState.update { it.copy(updateOperation = MaskedEmailDetailUpdate.SAVE_CHANGES) }
        viewModelScope.launch {
            maskedEmailRepository.updateMaskedEmail(emailId, params).fold(
                onSuccess = {
                    loadEmail(resetEdits = false)
                    _events.send(MaskedEmailDetailEvent.Updated)
                },
                onFailure = { error ->
                    _uiState.update {
                        it.copy(
                            updateOperation = null,
                            error = UiErrors.of(error, R.string.email_detail_error_save).withoutRetry()
                        )
                    }
                }
            )
        }
    }

    fun delete() {
        if (_uiState.value.isDeleting || _uiState.value.isUpdating) return
        _uiState.update { it.copy(isDeleting = true) }
        viewModelScope.launch {
            maskedEmailRepository.archiveMaskedEmail(emailId).fold(
                onSuccess = {
                    // Carry the pre-archive state so Undo restores it exactly: a DISABLED
                    // mask must not come back accepting mail.
                    _events.send(
                        MaskedEmailDetailEvent.Deleted(
                            id = emailId,
                            previousState = _uiState.value.email?.state ?: EmailState.ENABLED,
                        )
                    )
                    // After the event, so the delete button cannot flash back for a frame.
                    _uiState.update { it.copy(isDeleting = false) }
                },
                onFailure = { error ->
                    _uiState.update {
                        it.copy(
                            isDeleting = false,
                            error = UiErrors.of(error, R.string.email_detail_error_delete).withoutRetry()
                        )
                    }
                }
            )
        }
    }
}

data class MaskedEmailDetailUiState(
    val isLoading: Boolean = false,
    val updateOperation: MaskedEmailDetailUpdate? = null,
    val isDeleting: Boolean = false,
    val email: MaskedEmail? = null,
    val isEditing: Boolean = false,
    val editedDescription: String = "",
    val editedForDomain: String = "",
    val editedUrl: String = "",
    val error: UiError? = null,
) {
    val isUpdating: Boolean get() = updateOperation != null

    val hasChanges: Boolean get() = pendingChanges != null

    val pendingChanges: UpdateMaskedEmailParams?
        get() {
            val current = email ?: return null
            // Empty string clears a field; null leaves its server value unchanged.
            val params = UpdateMaskedEmailParams(
                description = editedDescription.trimmedIfChangedFrom(current.description),
                forDomain = editedForDomain.trimmedIfChangedFrom(current.forDomain),
                url = editedUrl.trimmedIfChangedFrom(current.url),
            )
            return params.takeIf { it.description != null || it.forDomain != null || it.url != null }
        }
}

enum class MaskedEmailDetailUpdate {
    TOGGLE_STATE,
    SAVE_CHANGES,
}

sealed class MaskedEmailDetailEvent {
    data object Updated : MaskedEmailDetailEvent()
    data class Deleted(val id: String, val previousState: EmailState) : MaskedEmailDetailEvent()
}

private const val KEY_EDITING = "isEditing"
