package com.fastmask.ui.list

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fastmask.R
import com.fastmask.data.local.SettingsDataStore
import com.fastmask.domain.model.AppMode
import com.fastmask.domain.model.EmailState
import com.fastmask.domain.model.MaskedEmail
import com.fastmask.domain.model.UpdateMaskedEmailParams
import com.fastmask.domain.usecase.GetCachedMaskedEmailsUseCase
import com.fastmask.domain.usecase.GetMaskedEmailsUseCase
import com.fastmask.domain.usecase.UpdateMaskedEmailUseCase
import com.fastmask.di.ApplicationScope
import com.fastmask.ui.common.UiErrors
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class MaskedEmailListViewModel @Inject constructor(
    private val getMaskedEmailsUseCase: GetMaskedEmailsUseCase,
    private val getCachedMaskedEmailsUseCase: GetCachedMaskedEmailsUseCase,
    private val updateMaskedEmailUseCase: UpdateMaskedEmailUseCase,
    private val settingsDataStore: SettingsDataStore,
    @ApplicationScope private val appScope: CoroutineScope,
) : ViewModel() {

    /** Live app-mode flag used by the screen to gate the demo tutorial overlay. */
    val appMode: StateFlow<AppMode> = settingsDataStore.appMode.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = settingsDataStore.appModeBlocking(),
    )

    /** Whether the user has already seen the demo tutorial. */
    val tutorialCompleted: StateFlow<Boolean> = settingsDataStore.tutorialCompleted.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = false,
    )

    // A DataStore write failure (disk full / corruption) must degrade quietly,
    // not crash: viewModelScope rethrows uncaught exceptions on the main thread.
    private val writeErrorHandler = CoroutineExceptionHandler { _, _ -> }

    fun markTutorialCompleted() {
        viewModelScope.launch(writeErrorHandler) {
            settingsDataStore.setTutorialCompleted(true)
        }
    }

    /**
     * Restore a just-archived mask (the "Undo" snackbar action) to the state it
     * had BEFORE archiving — undo promises the previous state, so a DISABLED
     * mask must not come back accepting mail.
     */
    fun restoreMask(id: String, restoreTo: EmailState = EmailState.ENABLED) {
        viewModelScope.launch {
            // The REQUEST runs in the application scope; only the wait for it
            // belongs to this ViewModel — the same split create and detail
            // already use. Undo was the last mutation still issued straight
            // from viewModelScope, and it is the one whose entire promise is
            // reversal: leaving the app while the restore was in flight
            // cancelled the PUT, so the mask stayed archived and the .onFailure
            // branch below — living in the same cancelled coroutine — never ran
            // to say so. If the request had already reached Fastmail, the mask
            // came back on the account while the list never reloaded.
            appScope.async {
                updateMaskedEmailUseCase(id, UpdateMaskedEmailParams(state = restoreTo))
            }.await()
                .onSuccess { loadMaskedEmails() }
                .onFailure { error ->
                    // Undo silently "succeeding" while the mask stays archived
                    // is worse than an error banner — surface it.
                    _uiState.update {
                        it.copy(errorRes = UiErrors.messageRes(error, R.string.email_detail_error_update))
                    }
                }
        }
    }

    private val _uiState = MutableStateFlow(MaskedEmailListUiState())
    val uiState: StateFlow<MaskedEmailListUiState> = _uiState.asStateFlow()

    init {
        loadMaskedEmails()
    }

    /**
     * True from the moment either entry point is called until its fetch
     * settles. Set synchronously, before the coroutine is dispatched, so two
     * calls in the same frame cannot both start a request.
     *
     * One flag for BOTH entry points on purpose: they used to guard
     * separately, and a silent refresh does not raise `isLoading` when the
     * list already has data — so a pull-to-refresh landing mid-refresh saw
     * `isLoading == false`, passed its own guard, and ran a second concurrent
     * fetch whose result raced the first (last writer wins).
     */
    private var fetchInFlight = false

    /**
     * Explicit, user-visible load: pull-to-refresh, the error-state retry, and
     * the reload after a successful undo. Always shows the spinner, and always
     * surfaces a failure — the user asked for this and is waiting on it.
     */
    fun loadMaskedEmails() = fetch(userInitiated = true)

    /**
     * Silent on-resume refresh. Shows the spinner only when there is nothing on
     * screen yet, and swallows a failure when there is — replacing good data
     * with an error banner because a background refresh failed is worse than
     * showing slightly stale masks.
     */
    fun refreshMaskedEmails() = fetch(userInitiated = false)

    private fun fetch(userInitiated: Boolean) {
        if (fetchInFlight) return
        fetchInFlight = true

        val hadData = _uiState.value.emails.isNotEmpty()
        if (userInitiated || !hadData) {
            _uiState.update { it.copy(isLoading = true, errorRes = null) }
        }

        viewModelScope.launch {
            try {
                getMaskedEmailsUseCase().fold(
                    onSuccess = { emails ->
                        _uiState.update {
                            it.copy(
                                isLoading = false,
                                emails = emails.sortedByDescending { email ->
                                    email.lastMessageAt ?: email.createdAt
                                },
                                filteredEmails = filterEmails(
                                    emails,
                                    it.searchQuery,
                                    it.selectedFilter,
                                ),
                                // A successful fetch is by definition current.
                                cachedAt = null,
                                // A previous pull-to-refresh or Undo failure is
                                // no longer true once the server answers again.
                                errorRes = null,
                            )
                        }
                    },
                    onFailure = { error ->
                        // Nothing on screen and no network: fall back to the
                        // last good snapshot rather than an empty error state.
                        // The most common thing a user opens this app to do is
                        // read back an address they already created, and that
                        // should not require a connection.
                        val cached = if (_uiState.value.emails.isEmpty()) {
                            getCachedMaskedEmailsUseCase()
                        } else {
                            null
                        }
                        _uiState.update {
                            when {
                                cached != null && cached.masks.isNotEmpty() -> it.copy(
                                    isLoading = false,
                                    emails = cached.masks.sortedByDescending { email ->
                                        email.lastMessageAt ?: email.createdAt
                                    },
                                    filteredEmails = filterEmails(
                                        cached.masks, it.searchQuery, it.selectedFilter,
                                    ),
                                    // Shown, never hidden: presenting stale
                                    // masks as current would be a quiet lie
                                    // about which addresses still exist.
                                    cachedAt = cached.cachedAt,
                                    errorRes = null,
                                )
                                userInitiated || it.emails.isEmpty() -> it.copy(
                                    isLoading = false,
                                    errorRes = UiErrors.messageRes(error, R.string.error_load_emails),
                                )
                                else -> it.copy(isLoading = false)
                            }
                        }
                    },
                )
            } finally {
                fetchInFlight = false
            }
        }
    }

    fun onSearchQueryChange(query: String) {
        _uiState.update {
            it.copy(
                searchQuery = query,
                filteredEmails = filterEmails(it.emails, query, it.selectedFilter)
            )
        }
    }

    fun onFilterChange(filter: EmailFilter) {
        _uiState.update {
            it.copy(
                selectedFilter = filter,
                filteredEmails = filterEmails(it.emails, it.searchQuery, filter)
            )
        }
    }

    private fun filterEmails(
        emails: List<MaskedEmail>,
        query: String,
        filter: EmailFilter
    ): List<MaskedEmail> {
        return emails
            .filter { email ->
                when (filter) {
                    EmailFilter.ALL -> true
                    // "Active" must match the chip count, which includes PENDING
                    // (a freshly created mask is pending until its first message).
                    EmailFilter.ENABLED -> email.isActive
                    EmailFilter.DISABLED -> email.state == EmailState.DISABLED
                    EmailFilter.DELETED -> email.state == EmailState.DELETED
                }
            }
            .filter { email ->
                if (query.isBlank()) true
                else {
                    email.email.contains(query, ignoreCase = true) ||
                            email.description?.contains(query, ignoreCase = true) == true ||
                            email.forDomain?.contains(query, ignoreCase = true) == true
                }
            }
            .sortedByDescending { it.lastMessageAt ?: it.createdAt }
    }
}

data class MaskedEmailListUiState(
    val isLoading: Boolean = false,
    val emails: List<MaskedEmail> = emptyList(),
    val filteredEmails: List<MaskedEmail> = emptyList(),
    val searchQuery: String = "",
    /** Non-null when the list is a cached snapshot taken at this instant. */
    val cachedAt: java.time.Instant? = null,
    val selectedFilter: EmailFilter = EmailFilter.ALL,
    val errorRes: Int? = null
) {
    /** An error shown above an existing list instead of replacing its data. */
    val inlineErrorRes: Int?
        get() = errorRes?.takeIf { emails.isNotEmpty() }
}

enum class EmailFilter {
    ALL, ENABLED, DISABLED, DELETED
}
