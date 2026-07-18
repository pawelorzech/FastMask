package com.fastmask.ui.list

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fastmask.R
import com.fastmask.data.local.SettingsDataStore
import com.fastmask.domain.model.AppMode
import com.fastmask.domain.model.EmailState
import com.fastmask.domain.model.MaskedEmail
import com.fastmask.domain.usecase.GetMaskedEmailsUseCase
import com.fastmask.ui.common.UiErrors
import dagger.hilt.android.lifecycle.HiltViewModel
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
    private val settingsDataStore: SettingsDataStore,
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

    fun markTutorialCompleted() {
        viewModelScope.launch {
            settingsDataStore.setTutorialCompleted(true)
        }
    }

    private val _uiState = MutableStateFlow(MaskedEmailListUiState())
    val uiState: StateFlow<MaskedEmailListUiState> = _uiState.asStateFlow()

    init {
        loadMaskedEmails()
    }

    fun loadMaskedEmails() {
        // Set synchronously (before the coroutine is dispatched) so the guard in
        // refreshMaskedEmails() sees the in-flight load immediately.
        _uiState.update { it.copy(isLoading = true, errorRes = null) }
        viewModelScope.launch {
            getMaskedEmailsUseCase().fold(
                onSuccess = { emails ->
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            emails = emails.sortedByDescending { email -> email.lastMessageAt ?: email.createdAt },
                            filteredEmails = filterEmails(
                                emails,
                                it.searchQuery,
                                it.selectedFilter
                            )
                        )
                    }
                },
                onFailure = { error ->
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            errorRes = UiErrors.messageRes(error, R.string.error_load_emails)
                        )
                    }
                }
            )
        }
    }

    fun refreshMaskedEmails() {
        // The init load is already in flight on first entry — the on-resume
        // refresh would duplicate the network call the user is waiting on.
        if (_uiState.value.isLoading) return
        viewModelScope.launch {
            // Don't show loading if we already have data (soft refresh)
            if (_uiState.value.emails.isEmpty()) {
                _uiState.update { it.copy(isLoading = true, errorRes = null) }
            }

            getMaskedEmailsUseCase().fold(
                onSuccess = { emails ->
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            emails = emails.sortedByDescending { email -> email.lastMessageAt ?: email.createdAt },
                            filteredEmails = filterEmails(
                                emails,
                                it.searchQuery,
                                it.selectedFilter
                            )
                        )
                    }
                },
                onFailure = { error ->
                    // Only show error if we have no data
                    if (_uiState.value.emails.isEmpty()) {
                        _uiState.update {
                            it.copy(
                                isLoading = false,
                                errorRes = UiErrors.messageRes(error, R.string.error_load_emails)
                            )
                        }
                    } else {
                        _uiState.update { it.copy(isLoading = false) }
                    }
                }
            )
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
    val selectedFilter: EmailFilter = EmailFilter.ALL,
    val errorRes: Int? = null
)

enum class EmailFilter {
    ALL, ENABLED, DISABLED, DELETED
}
