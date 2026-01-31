package com.fastmask.ui.list

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fastmask.domain.model.EmailState
import com.fastmask.domain.model.MaskedEmail
import com.fastmask.domain.usecase.GetMaskedEmailsUseCase
import com.fastmask.domain.usecase.LogoutUseCase
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
class MaskedEmailListViewModel @Inject constructor(
    private val getMaskedEmailsUseCase: GetMaskedEmailsUseCase,
    private val logoutUseCase: LogoutUseCase
) : ViewModel() {

    private val _uiState = MutableStateFlow(MaskedEmailListUiState())
    val uiState: StateFlow<MaskedEmailListUiState> = _uiState.asStateFlow()

    private val _events = MutableSharedFlow<MaskedEmailListEvent>()
    val events: SharedFlow<MaskedEmailListEvent> = _events.asSharedFlow()

    init {
        loadMaskedEmails()
    }

    fun loadMaskedEmails() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }

            getMaskedEmailsUseCase().fold(
                onSuccess = { emails ->
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            emails = emails.sortedByDescending { email -> email.createdAt },
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
                            error = error.message ?: "Failed to load emails"
                        )
                    }
                }
            )
        }
    }

    fun refreshMaskedEmails() {
        viewModelScope.launch {
            // Don't show loading if we already have data (soft refresh)
            if (_uiState.value.emails.isEmpty()) {
                _uiState.update { it.copy(isLoading = true, error = null) }
            }

            getMaskedEmailsUseCase().fold(
                onSuccess = { emails ->
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            emails = emails.sortedByDescending { email -> email.createdAt },
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
                                error = error.message ?: "Failed to load emails"
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

    fun logout() {
        logoutUseCase()
        viewModelScope.launch {
            _events.emit(MaskedEmailListEvent.LoggedOut)
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
                    EmailFilter.ENABLED -> email.state == EmailState.ENABLED
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
            .sortedByDescending { it.createdAt }
    }
}

data class MaskedEmailListUiState(
    val isLoading: Boolean = false,
    val emails: List<MaskedEmail> = emptyList(),
    val filteredEmails: List<MaskedEmail> = emptyList(),
    val searchQuery: String = "",
    val selectedFilter: EmailFilter = EmailFilter.ALL,
    val error: String? = null
)

enum class EmailFilter {
    ALL, ENABLED, DISABLED, DELETED
}

sealed class MaskedEmailListEvent {
    data object LoggedOut : MaskedEmailListEvent()
}
