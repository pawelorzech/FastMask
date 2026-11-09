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

    // One-shot results of the FAB menu's "Random mask". Channel-backed so an
    // event emitted while the screen is mid-rotation is delivered once, later.
    private val _events = Channel<MaskedEmailListEvent>(Channel.BUFFERED)
    val events: Flow<MaskedEmailListEvent> = _events.receiveAsFlow()

    /**
     * Creates a mask with no prefix, domain or note — Fastmail picks the
     * address — through the same repository call as the create screen, so demo
     * mode behaves identically.
     */
    fun createRandomMask() {
        if (_uiState.value.isCreatingRandom) return
        _uiState.update { it.copy(isCreatingRandom = true) }
        viewModelScope.launch {
            maskedEmailRepository.createMaskedEmail(CreateMaskedEmailParams()).fold(
                onSuccess = { created ->
                    _uiState.update { it.copy(isCreatingRandom = false) }
                    // The new mask reaches the list through the repository's
                    // `changes` signal, which refreshes silently.
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

    /**
     * Restore a just-archived mask (the "Undo" snackbar action) to the state it
     * had BEFORE archiving — undo promises the previous state, so a DISABLED
     * mask must not come back accepting mail.
     * The list reloads through the repository's `changes` signal.
     */
    fun restoreMask(id: String, restoreTo: EmailState) {
        viewModelScope.launch {
            maskedEmailRepository.updateMaskedEmail(id, UpdateMaskedEmailParams(state = restoreTo))
                .onFailure { error ->
                    // Undo silently "succeeding" while the mask stays archived
                    // is worse than an error banner — surface it.
                    _uiState.update {
                        it.copy(error = UiErrors.of(error, R.string.error_update_mask, UiErrorAction.RETRY))
                    }
                }
        }
    }

    private val _uiState = MutableStateFlow(MaskedEmailListUiState())
    val uiState: StateFlow<MaskedEmailListUiState> = _uiState.asStateFlow()

    /** Test seam for [refreshIfStale]. */
    internal var clockMillis: () -> Long = System::currentTimeMillis

    /** Null until the first fetch, and again after a failed one so the next resume retries. */
    private var lastFetchStartedAt: Long? = null

    /**
     * One flag for BOTH entry points on purpose: a silent refresh does not
     * raise `isLoading` when the list already has data, so separate guards let
     * a pull-to-refresh landing mid-refresh run a second concurrent fetch whose
     * result raced the first. A request that arrives mid-fetch is coalesced
     * into one re-run, which is user-initiated if any of the requests was, so
     * its spinner and error reporting are honoured.
     */
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
        // Any successful create / edit / toggle / archive, from any screen or
        // service, makes what is on screen stale: refetch silently. This is what
        // keeps the list current; the resume throttle below only damps flapping.
        viewModelScope.launch { maskedEmailRepository.changes.collect { refreshMaskedEmails() } }
    }

    /**
     * Explicit, user-visible load: pull-to-refresh, the error-state retry, and
     * the retry after a failed fetch. Always shows the spinner, and always
     * surfaces a failure — the user asked for this and is waiting on it.
     */
    fun loadMaskedEmails() = fetchFlight.request(true)

    /**
     * Silent on-resume refresh. Shows the spinner only when there is nothing on
     * screen yet, and swallows a failure when there is — replacing good data
     * with an error banner because a background refresh failed is worse than
     * showing slightly stale masks.
     */
    fun refreshMaskedEmails() = fetchFlight.request(false)

    /**
     * The on-resume refresh: only when the last fetch began more than
     * [STALE_AFTER_MS] ago (or failed). Returning to the screen right after the
     * initial load, or flipping between background and foreground, costs no
     * second request; changes made in this app arrive through `changes`.
     */
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
                        // A previous pull-to-refresh or Undo failure is
                        // no longer true once the server answers again.
                        error = null,
                    )
                }
            },
            onFailure = { error ->
                lastFetchStartedAt = null
                // Nothing on screen and no network: fall back to the
                // last good snapshot rather than an empty error state.
                // The most common thing a user opens this app to do is
                // read back an address they already created, and that
                // should not require a connection.
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
                            // Shown, never hidden: presenting stale
                            // masks as current would be a quiet lie
                            // about which addresses still exist.
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
        // A hardware keyboard / IME paste bypasses any custom paste action.
        // A search term longer than this has no product value, but keeping and
        // comparing an unbounded document on every keystroke can make typing
        // visibly lag on large mask lists.
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
    /** Non-null when the list is a cached snapshot taken at this instant. */
    val cachedAt: java.time.Instant? = null,
    val selectedFilter: EmailFilter = EmailFilter.ALL,
    val error: UiError? = null,
    /** True while the FAB menu's "Random mask" request is in flight. */
    val isCreatingRandom: Boolean = false,
) {
    /** An error shown above an existing list instead of replacing its data. */
    val inlineError: UiError?
        get() = error?.takeIf { emails.isNotEmpty() }

    /**
     * What the list shows: [emails] narrowed by [searchQuery] and
     * [selectedFilter]. Derived here, once per state instance, so no code path
     * can change one input and forget to refilter.
     */
    val filteredEmails: List<MaskedEmail> by lazy(LazyThreadSafetyMode.NONE) {
        filterMasks(emails, searchQuery, selectedFilter)
    }
}

/** The list's order: most recently active first. */
private fun List<MaskedEmail>.mostRecentFirst(): List<MaskedEmail> =
    sortedByDescending { mask -> mask.lastActivityAt }

sealed interface MaskedEmailListEvent {
    data class RandomCreated(val email: String) : MaskedEmailListEvent
    data class RandomFailed(@StringRes val messageRes: Int) : MaskedEmailListEvent
}

