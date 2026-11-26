package com.fastmask.ui.detail

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fastmask.R
import com.fastmask.domain.model.EmailState
import com.fastmask.domain.model.MaskedEmail
import com.fastmask.domain.model.MaskedEmailMetadata
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

    private var currentEmailId: String = savedStateHandle.get<String>(NavRoutes.ARG_EMAIL_ID).orEmpty()

    val emailId: String
        get() = currentEmailId

    private val initialEmail = currentEmailId.takeIf { it.isNotEmpty() }
        ?.let(maskedEmailRepository::peekMaskedEmail)

    private val _uiState = MutableStateFlow(
        MaskedEmailDetailUiState(
            email = initialEmail,
            isEditing = savedStateHandle[KEY_EDITING] ?: false,
            editedDescription = savedStateHandle.get<String>(KEY_EDITED_DESCRIPTION)
                ?: initialEmail?.description.orEmpty(),
            editedForDomain = savedStateHandle.get<String>(KEY_EDITED_FOR_DOMAIN)
                ?: initialEmail?.forDomain.orEmpty(),
            editedUrl = savedStateHandle.get<String>(KEY_EDITED_URL)
                ?: initialEmail?.url.orEmpty(),
        ),
    )
    val uiState: StateFlow<MaskedEmailDetailUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch(Dispatchers.Unconfined) {
            _uiState.map { it.isEditing }.distinctUntilChanged().collect { savedStateHandle[KEY_EDITING] = it }
        }
    }

    private val _events = Channel<MaskedEmailDetailEvent>(Channel.BUFFERED)
    val events: Flow<MaskedEmailDetailEvent> = _events.receiveAsFlow()

    private data class SavedDraft(
        val description: String,
        val forDomain: String,
        val url: String,
    )

    private data class LoadRequest(
        val emailId: String,
        val resetEdits: Boolean,
        val savedDraft: SavedDraft? = null,
    )

    private val loadFlight = SingleFlight<LoadRequest>(
        scope = viewModelScope,
        merge = { queued, new ->
            if (queued.emailId != new.emailId) {
                new
            } else {
                LoadRequest(
                    emailId = new.emailId,
                    resetEdits = queued.resetEdits && new.resetEdits,
                    savedDraft = new.savedDraft ?: queued.savedDraft,
                )
            }
        },
        start = { request ->
            // A failed refresh with a mask already on screen (cache seed or an earlier
            // success) degrades to the inline error above Save: the full-screen error
            // state would throw away what the user is looking at.
            val hadEmail = _uiState.value.email?.id == request.emailId
            if (!hadEmail) {
                _uiState.update { it.copy(isLoading = true, error = null) }
            }
            suspend { loadOnce(request, hadEmail) }
        },
    )

    init {
        if (currentEmailId.isNotEmpty()) {
            loadEmail()
        }
    }

    /**
     * Switches the workspace detail pane to [newEmailId], seeding synchronously from the RAM cache
     * on frame 0 so switching masks never flashes a loading indicator.
     */
    fun openEmail(newEmailId: String) {
        if (newEmailId.isBlank()) return
        if (newEmailId == currentEmailId && (_uiState.value.email?.id == newEmailId || _uiState.value.isLoading)) {
            return
        }
        val sameSavedId = savedStateHandle.get<String>(NavRoutes.ARG_EMAIL_ID) == newEmailId
        currentEmailId = newEmailId
        savedStateHandle[NavRoutes.ARG_EMAIL_ID] = newEmailId
        if (!sameSavedId) {
            clearSavedDrafts()
            savedStateHandle[KEY_EDITING] = false
        }
        val peeked = maskedEmailRepository.peekMaskedEmail(newEmailId)
        _uiState.value = MaskedEmailDetailUiState(
            email = peeked,
            isLoading = peeked == null,
            isEditing = if (sameSavedId) (savedStateHandle[KEY_EDITING] ?: false) else false,
            editedDescription = if (sameSavedId) {
                savedStateHandle.get<String>(KEY_EDITED_DESCRIPTION) ?: peeked?.description.orEmpty()
            } else {
                peeked?.description.orEmpty()
            },
            editedForDomain = if (sameSavedId) {
                savedStateHandle.get<String>(KEY_EDITED_FOR_DOMAIN) ?: peeked?.forDomain.orEmpty()
            } else {
                peeked?.forDomain.orEmpty()
            },
            editedUrl = if (sameSavedId) {
                savedStateHandle.get<String>(KEY_EDITED_URL) ?: peeked?.url.orEmpty()
            } else {
                peeked?.url.orEmpty()
            },
        )
        loadEmail(resetEdits = true)
    }

    // resetEdits is false for the reload after a save or toggle: overwriting edited*
    // there would drop keystrokes typed while the call was in flight.
    fun loadEmail(resetEdits: Boolean = true) {
        val targetId = currentEmailId
        if (targetId.isEmpty()) return
        loadFlight.request(LoadRequest(emailId = targetId, resetEdits = resetEdits))
    }

    private suspend fun loadOnce(request: LoadRequest, hadEmail: Boolean) {
        val targetId = request.emailId
        if (targetId != currentEmailId) return
        if (!hadEmail && _uiState.value.email?.id != targetId) {
            seedFromCache(request)
        }

        maskedEmailRepository.getMaskedEmail(targetId).fold(
            onSuccess = { email ->
                if (targetId != currentEmailId) return@fold
                if (email != null) {
                    applyLoadedEmail(email, request)
                    _uiState.update { it.copy(error = null) }
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
                if (targetId != currentEmailId) return@fold
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        updateOperation = null,
                        error = UiErrors.of(error, R.string.email_detail_error_load, retryable = true)
                    )
                }
            }
        )
    }

    private suspend fun seedFromCache(request: LoadRequest) {
        val targetId = request.emailId
        val cached = maskedEmailRepository.cachedMaskedEmails()?.masks?.find { it.id == targetId } ?: return
        if (targetId != currentEmailId || _uiState.value.email?.id == targetId) return
        applyLoadedEmail(cached, request)
    }

    private fun applyLoadedEmail(email: MaskedEmail, request: LoadRequest) {
        _uiState.update {
            // Sync back fields whose draft still matches what we just saved (so server-side
            // normalization like figma.com -> https://figma.com settles the draft), while
            // keeping any keystrokes typed while the save/reload was in flight.
            fun refreshedField(
                draft: String,
                previous: String?,
                fresh: String?,
                savedValue: String?,
                draftKey: String,
            ): String = when {
                savedValue != null && draft == savedValue -> fresh.orEmpty()
                savedStateHandle.contains(draftKey) -> draft
                request.resetEdits && draft == previous.orEmpty() -> fresh.orEmpty()
                else -> draft
            }

            val saved = request.savedDraft
            val loaded = it.copy(
                isLoading = false,
                updateOperation = null,
                email = email,
                editedDescription = refreshedField(
                    it.editedDescription,
                    it.email?.description,
                    email.description,
                    saved?.description,
                    KEY_EDITED_DESCRIPTION,
                ),
                editedForDomain = refreshedField(
                    it.editedForDomain,
                    it.email?.forDomain,
                    email.forDomain,
                    saved?.forDomain,
                    KEY_EDITED_FOR_DOMAIN,
                ),
                editedUrl = refreshedField(
                    it.editedUrl,
                    it.email?.url,
                    email.url,
                    saved?.url,
                    KEY_EDITED_URL,
                ),
            )
            // A save lands when the reload after it does: only then does the
            // draft match the server again and edit mode can close. A failed
            // save, or keystrokes typed while it was in flight, keep the draft
            // and stay in edit mode.
            if ((it.updateOperation == MaskedEmailDetailUpdate.SAVE_CHANGES || saved != null) && !loaded.hasChanges) {
                clearSavedDrafts()
                loaded.copy(isEditing = false)
            } else {
                loaded
            }
        }
    }

    fun onDescriptionChange(description: String) {
        savedStateHandle[KEY_EDITED_DESCRIPTION] = description
        _uiState.update { it.copy(editedDescription = description) }
    }

    fun onForDomainChange(domain: String) {
        savedStateHandle[KEY_EDITED_FOR_DOMAIN] = domain
        _uiState.update { it.copy(editedForDomain = domain) }
    }

    fun onUrlChange(url: String) {
        savedStateHandle[KEY_EDITED_URL] = url
        _uiState.update { it.copy(editedUrl = url) }
    }

    fun startEditing() {
        _uiState.update { it.copy(isEditing = true) }
    }

    fun discardEdits() {
        clearSavedDrafts()
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

    private fun clearSavedDrafts() {
        savedStateHandle.remove<String>(KEY_EDITED_DESCRIPTION)
        savedStateHandle.remove<String>(KEY_EDITED_FOR_DOMAIN)
        savedStateHandle.remove<String>(KEY_EDITED_URL)
    }

    fun enable() = updateState(EmailState.ENABLED)

    fun disable() = updateState(EmailState.DISABLED)

    private fun updateState(newState: EmailState) {
        val targetId = currentEmailId
        if (targetId.isEmpty() || _uiState.value.isUpdating || _uiState.value.isDeleting) return
        _uiState.update { it.copy(updateOperation = MaskedEmailDetailUpdate.TOGGLE_STATE, error = null) }
        viewModelScope.launch {
            maskedEmailRepository.updateMaskedEmail(targetId, UpdateMaskedEmailParams(state = newState)).fold(
                onSuccess = {
                    if (targetId != currentEmailId) return@fold
                    loadEmail(resetEdits = false)
                    _events.send(MaskedEmailDetailEvent.Updated)
                },
                onFailure = { error ->
                    if (targetId != currentEmailId) return@fold
                    _uiState.update {
                        it.copy(
                            updateOperation = null,
                            error = UiErrors.of(error, R.string.error_update_mask, retryable = false)
                        )
                    }
                }
            )
        }
    }

    fun saveChanges() {
        val targetId = currentEmailId
        val state = _uiState.value
        if (targetId.isEmpty() || state.isUpdating || state.isDeleting) return
        val params = state.pendingChanges ?: return
        val savedDraft = SavedDraft(
            description = state.editedDescription,
            forDomain = state.editedForDomain,
            url = state.editedUrl,
        )

        _uiState.update { it.copy(updateOperation = MaskedEmailDetailUpdate.SAVE_CHANGES, error = null) }
        viewModelScope.launch {
            maskedEmailRepository.updateMaskedEmail(targetId, params).fold(
                onSuccess = {
                    if (targetId != currentEmailId) return@fold
                    loadFlight.request(LoadRequest(emailId = targetId, resetEdits = false, savedDraft = savedDraft))
                    _events.send(MaskedEmailDetailEvent.Updated)
                },
                onFailure = { error ->
                    if (targetId != currentEmailId) return@fold
                    _uiState.update {
                        it.copy(
                            updateOperation = null,
                            error = UiErrors.of(error, R.string.email_detail_error_save, retryable = false)
                        )
                    }
                }
            )
        }
    }

    fun delete() {
        val targetId = currentEmailId
        if (targetId.isEmpty() || _uiState.value.isDeleting || _uiState.value.isUpdating) return
        _uiState.update { it.copy(isDeleting = true, error = null) }
        viewModelScope.launch {
            maskedEmailRepository.archiveMaskedEmail(targetId).fold(
                onSuccess = {
                    if (targetId != currentEmailId) return@fold
                    // Carry the pre-archive state so Undo restores it exactly: a DISABLED
                    // mask must not come back accepting mail.
                    _events.send(
                        MaskedEmailDetailEvent.Deleted(
                            id = targetId,
                            previousState = _uiState.value.email?.state ?: EmailState.ENABLED,
                        )
                    )
                    // After the event, so the delete button cannot flash back for a frame.
                    _uiState.update { it.copy(isDeleting = false) }
                },
                onFailure = { error ->
                    if (targetId != currentEmailId) return@fold
                    _uiState.update {
                        it.copy(
                            isDeleting = false,
                            error = UiErrors.of(error, R.string.email_detail_error_delete, retryable = false)
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
            // Comparing against the normalized origin avoids treating "figma.com" as dirty
            // when the server already holds "https://figma.com"; invalid input still stays dirty.
            val forDomainChange = editedForDomain.trimmedIfChangedFrom(current.forDomain)?.takeIf { raw ->
                val normalized = MaskedEmailMetadata.normalizeForDomain(raw)
                normalized == null || normalized != current.forDomain.orEmpty()
            }
            // Empty string clears a field; null leaves its server value unchanged.
            val params = UpdateMaskedEmailParams(
                description = editedDescription.trimmedIfChangedFrom(current.description),
                forDomain = forDomainChange,
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
private const val KEY_EDITED_DESCRIPTION = "editedDescription"
private const val KEY_EDITED_FOR_DOMAIN = "editedForDomain"
private const val KEY_EDITED_URL = "editedUrl"
