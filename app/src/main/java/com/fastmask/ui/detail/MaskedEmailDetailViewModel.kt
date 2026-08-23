package com.fastmask.ui.detail

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fastmask.R
import com.fastmask.domain.model.EmailState
import com.fastmask.domain.model.MaskedEmail
import com.fastmask.domain.model.UpdateMaskedEmailParams
import com.fastmask.domain.usecase.ArchiveMaskedEmailUseCase
import com.fastmask.domain.usecase.GetCachedMaskedEmailsUseCase
import com.fastmask.domain.usecase.GetMaskedEmailUseCase
import com.fastmask.domain.usecase.UpdateMaskedEmailUseCase
import com.fastmask.ui.common.UiErrors
import com.fastmask.di.ApplicationScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.async
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
    private val getMaskedEmailUseCase: GetMaskedEmailUseCase,
    private val getCachedMaskedEmailsUseCase: GetCachedMaskedEmailsUseCase,
    private val updateMaskedEmailUseCase: UpdateMaskedEmailUseCase,
    private val archiveMaskedEmailUseCase: ArchiveMaskedEmailUseCase,
    @ApplicationScope private val appScope: CoroutineScope,
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
     * True from the moment a load starts until its network leg settles. Set
     * synchronously so a retry double-tap cannot stack two fetches whose
     * results would race.
     */
    private var loadInFlight = false

    /**
     * @param resetEdits when true (initial load / retry) the editable fields are
     *   (re)seeded from the server value. The reload triggered AFTER a save or a
     *   state toggle passes false: overwriting `edited*` there would drop any
     *   keystrokes the user typed while the network call was in flight.
     */
    fun loadEmail(resetEdits: Boolean = true) {
        if (loadInFlight) return
        loadInFlight = true

        // Stale-while-revalidate, two legs:
        //
        // 1. The encrypted last-known-good snapshot usually already holds this
        //    mask — the user just saw it on the list. Seeding from it renders
        //    the screen instantly instead of holding a spinner for a network
        //    round trip.
        // 2. A single-mask JMAP get (`ids: [id]`) then confirms or corrects it.
        //    This replaced the old full-account fetch that ran on every open
        //    and every post-edit reload — on large accounts the most expensive
        //    read in the app for the least data.
        //
        // When the refresh fails but a mask is already on screen (cache seed
        // or an earlier success), the failure degrades to the inline error
        // text above Save; replacing usable content with the full-screen
        // error state would throw away what the user is looking at.
        val hadEmail = _uiState.value.email != null
        if (!hadEmail) {
            _uiState.update { it.copy(isLoading = true, errorRes = null) }
        }

        viewModelScope.launch {
            try {
                if (!hadEmail && _uiState.value.email == null) {
                    seedFromCache(resetEdits)
                }

                getMaskedEmailUseCase(emailId).fold(
                    onSuccess = { email ->
                        if (email != null) {
                            applyLoadedEmail(email, resetEdits)
                        } else {
                            // Server does not know this id: deleted elsewhere,
                            // or a stale deep link. Same surface as before —
                            // full-screen only when nothing is on screen yet.
                            _uiState.update {
                                it.copy(
                                    isLoading = false,
                                    updateOperation = null,
                                    errorRes = R.string.email_detail_error_load
                                )
                            }
                        }
                    },
                    onFailure = { error ->
                        _uiState.update {
                            it.copy(
                                isLoading = false,
                                updateOperation = null,
                                errorRes = UiErrors.messageRes(error, R.string.email_detail_error_load)
                            )
                        }
                    }
                )
            } finally {
                loadInFlight = false
            }
        }
    }

    private suspend fun seedFromCache(resetEdits: Boolean) {
        val cached = getCachedMaskedEmailsUseCase()?.masks?.find { it.id == emailId } ?: return
        // The fresh fetch may have landed while the cache was being decrypted.
        if (_uiState.value.email != null) return
        applyLoadedEmail(cached, resetEdits)
    }

    private fun applyLoadedEmail(email: MaskedEmail, resetEdits: Boolean) {
        _uiState.update {
            it.copy(
                isLoading = false,
                updateOperation = null,
                email = email,
                editedDescription = if (resetEdits) email.description ?: "" else it.editedDescription,
                editedForDomain = if (resetEdits) email.forDomain ?: "" else it.editedForDomain,
                editedUrl = if (resetEdits) email.url ?: "" else it.editedUrl
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
        _uiState.update { it.copy(updateOperation = MaskedEmailDetailUpdate.TOGGLE_STATE) }
        viewModelScope.launch {

            // Application scope: see the note in create(). Leaving the screen
            // mid-request must not cancel a change the server may already have
            // applied, leaving the UI and the account disagreeing.
            appScope.async {
                updateMaskedEmailUseCase(emailId, UpdateMaskedEmailParams(state = newState))
            }.await().fold(
                onSuccess = {
                    loadEmail(resetEdits = false)
                    _events.send(MaskedEmailDetailEvent.Updated)
                },
                onFailure = { error ->
                    _uiState.update {
                        it.copy(
                            updateOperation = null,
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
        _uiState.update { it.copy(updateOperation = MaskedEmailDetailUpdate.SAVE_CHANGES) }
        viewModelScope.launch {

            appScope.async { updateMaskedEmailUseCase(emailId, params) }.await().fold(
                onSuccess = {
                    loadEmail(resetEdits = false)
                    _events.send(MaskedEmailDetailEvent.Updated)
                },
                onFailure = { error ->
                    _uiState.update {
                        it.copy(
                            updateOperation = null,
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

            // The archive in particular: cancelling it after the server acted
            // meant the mask was archived but the Deleted event never fired, so
            // the list showed no Undo snackbar for a change that had happened.
            appScope.async { archiveMaskedEmailUseCase(emailId) }.await().fold(
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
                    // After the event, so the screen is already navigating away
                    // and the delete button cannot flash back for a frame. The
                    // operation is finished either way, and leaving the flag set
                    // would strand this screen behind a spinner if the
                    // navigation it triggers never happens.
                    _uiState.update { it.copy(isDeleting = false) }
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
    val updateOperation: MaskedEmailDetailUpdate? = null,
    val isDeleting: Boolean = false,
    val email: MaskedEmail? = null,
    val editedDescription: String = "",
    val editedForDomain: String = "",
    val editedUrl: String = "",
    val errorRes: Int? = null,
) {
    val isUpdating: Boolean get() = updateOperation != null
}

enum class MaskedEmailDetailUpdate {
    TOGGLE_STATE,
    SAVE_CHANGES,
}

sealed class MaskedEmailDetailEvent {
    data object Updated : MaskedEmailDetailEvent()
    data class Deleted(val id: String, val previousState: EmailState) : MaskedEmailDetailEvent()
}
