package com.fastmask.ui.list

import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fastmask.R
import com.fastmask.domain.model.Session
import com.fastmask.domain.model.CreateMaskedEmailParams
import com.fastmask.domain.model.EmailState
import com.fastmask.domain.model.MaskedEmail
import com.fastmask.domain.model.UpdateMaskedEmailParams
import com.fastmask.domain.repository.SessionRepository
import com.fastmask.domain.repository.MaskedEmailRepository
import com.fastmask.core.SingleFlight
import com.fastmask.ui.common.UiError
import com.fastmask.ui.common.UiErrorAction
import com.fastmask.ui.common.UiErrors
import dagger.hilt.android.lifecycle.HiltViewModel
import com.fastmask.core.QuietWriteFailure
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class MaskedEmailListViewModel @Inject constructor(
    private val maskedEmailRepository: MaskedEmailRepository,
    private val sessionRepository: SessionRepository,
) : ViewModel() {

    private val _events = Channel<MaskedEmailListEvent>(Channel.BUFFERED)
    val events: Flow<MaskedEmailListEvent> = _events.receiveAsFlow()

    fun createRandomMask() {
        if (_uiState.value.isCreatingRandom) return
        _uiState.update { it.copy(isCreatingRandom = true) }
        viewModelScope.launch {
            maskedEmailRepository.createMaskedEmail(CreateMaskedEmailParams()).fold(
                onSuccess = { created ->
                    _uiState.update { it.copy(isCreatingRandom = false) }
                    _events.send(MaskedEmailListEvent.RandomCreated(created.email))
                },
                onFailure = { error ->
                    _uiState.update { it.copy(isCreatingRandom = false) }
                    _events.send(
                        MaskedEmailListEvent.RandomFailed(
                            UiErrors.messageRes(error, R.string.error_create_mask),
                        ),
                    )
                },
            )
        }
    }

    val session: StateFlow<Session> = sessionRepository.session

    val tutorialCompleted: StateFlow<Boolean> = sessionRepository.tutorialCompleted.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = false,
    )

    fun markTutorialCompleted() {
        viewModelScope.launch(QuietWriteFailure) {
            sessionRepository.completeTutorial()
        }
    }

    /** Undo promises the previous state: a DISABLED mask must not come back accepting mail. */
    fun restoreMask(id: String, restoreTo: EmailState) {
        viewModelScope.launch {
            maskedEmailRepository.updateMaskedEmail(id, UpdateMaskedEmailParams(state = restoreTo))
                .onFailure { error ->
                    // Undo silently "succeeding" while the mask stays archived is worse than an error banner.
                    _uiState.update {
                        it.copy(error = UiErrors.of(error, R.string.error_update_mask, UiErrorAction.RETRY))
                    }
                }
        }
    }

    private val _uiState = MutableStateFlow(MaskedEmailListUiState())
    val uiState: StateFlow<MaskedEmailListUiState> = _uiState.asStateFlow()

    internal var clockMillis: () -> Long = System::currentTimeMillis

    private var lastFetchStartedAt: Long? = null

    private val fetchFlight = SingleFlight<Boolean>(
        scope = viewModelScope,
        merge = { queued, new -> queued || new },
        start = { userInitiated ->
            val hadData = _uiState.value.emails.isNotEmpty()
            if (userInitiated || !hadData) {
                _uiState.update { it.copy(isLoading = true, error = null) }
            }
            lastFetchStartedAt = clockMillis()
            suspend { fetchOnce(userInitiated) }
        },
    )

    init {
        loadMaskedEmails()
        viewModelScope.launch { maskedEmailRepository.changes.collect { refreshMaskedEmails() } }
    }

    fun loadMaskedEmails() = fetchFlight.request(true)

    // Silent: a failed background refresh must not replace good data with an
    // error banner; slightly stale masks are better.
    fun refreshMaskedEmails() = fetchFlight.request(false)

    fun refreshIfStale() {
        val last = lastFetchStartedAt
        if (last == null || clockMillis() - last > STALE_AFTER_MS) refreshMaskedEmails()
    }

    private suspend fun fetchOnce(userInitiated: Boolean) {
        maskedEmailRepository.getMaskedEmails().fold(
            onSuccess = { emails ->
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        emails = emails.mostRecentFirst(),
                        cachedAt = null,
                        error = null,
                    )
                }
            },
            onFailure = { error ->
                lastFetchStartedAt = null
                // The most common reason to open the app is reading back an address
                // already created, which must not require a connection.
                val cached = if (_uiState.value.emails.isEmpty()) {
                    maskedEmailRepository.cachedMaskedEmails()
                } else {
                    null
                }
                _uiState.update {
                    when {
                        cached != null && cached.masks.isNotEmpty() -> it.copy(
                            isLoading = false,
                            emails = cached.masks.mostRecentFirst(),
                            // Shown, never hidden: presenting stale masks as current
                            // would be a quiet lie about which addresses still exist.
                            cachedAt = cached.cachedAt,
                            error = null,
                        )
                        userInitiated || it.emails.isEmpty() -> it.copy(
                            isLoading = false,
                            error = UiErrors.of(error, R.string.error_load_emails, UiErrorAction.RETRY),
                        )
                        else -> it.copy(isLoading = false)
                    }
                }
            },
        )
    }

    fun onSearchQueryChange(query: String) {
        // A hardware keyboard / IME paste bypasses any custom paste action, and
        // comparing an unbounded document on every keystroke makes typing lag.
        val boundedQuery = query.take(MAX_SEARCH_QUERY_LENGTH)
        _uiState.update { it.copy(searchQuery = boundedQuery) }
    }

    fun onFilterChange(filter: EmailFilter) {
        _uiState.update { it.copy(selectedFilter = filter) }
    }

    companion object {
        internal const val MAX_SEARCH_QUERY_LENGTH = 256
        internal const val STALE_AFTER_MS = 30_000L
    }
}

data class MaskedEmailListUiState(
    val isLoading: Boolean = false,
    val emails: List<MaskedEmail> = emptyList(),
    val searchQuery: String = "",
    val cachedAt: java.time.Instant? = null,
    val selectedFilter: EmailFilter = EmailFilter.ALL,
    val error: UiError? = null,
    val isCreatingRandom: Boolean = false,
) {
    val inlineError: UiError?
        get() = error?.takeIf { emails.isNotEmpty() }

    val filteredEmails: List<MaskedEmail> by lazy(LazyThreadSafetyMode.NONE) {
        filterMasks(emails, searchQuery, selectedFilter)
    }
}

private fun List<MaskedEmail>.mostRecentFirst(): List<MaskedEmail> =
    sortedByDescending { mask -> mask.lastActivityAt }

sealed interface MaskedEmailListEvent {
    data class RandomCreated(val email: String) : MaskedEmailListEvent
    data class RandomFailed(@StringRes val messageRes: Int) : MaskedEmailListEvent
}

