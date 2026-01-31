package com.fastmask.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fastmask.domain.model.Language
import com.fastmask.domain.usecase.GetCurrentLanguageUseCase
import com.fastmask.domain.usecase.LogoutUseCase
import com.fastmask.domain.usecase.SetLanguageUseCase
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
class SettingsViewModel @Inject constructor(
    private val getCurrentLanguageUseCase: GetCurrentLanguageUseCase,
    private val setLanguageUseCase: SetLanguageUseCase,
    private val logoutUseCase: LogoutUseCase
) : ViewModel() {

    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    private val _events = MutableSharedFlow<SettingsEvent>()
    val events: SharedFlow<SettingsEvent> = _events.asSharedFlow()

    init {
        loadCurrentLanguage()
    }

    private fun loadCurrentLanguage() {
        viewModelScope.launch {
            getCurrentLanguageUseCase().collect { language ->
                _uiState.update { it.copy(selectedLanguage = language) }
            }
        }
    }

    fun onLanguageSelected(language: Language?) {
        viewModelScope.launch {
            setLanguageUseCase(language)
            _uiState.update { it.copy(selectedLanguage = language) }
        }
    }

    fun logout() {
        logoutUseCase()
        viewModelScope.launch {
            _events.emit(SettingsEvent.LoggedOut)
        }
    }
}

data class SettingsUiState(
    val selectedLanguage: Language? = null
)

sealed class SettingsEvent {
    data object LoggedOut : SettingsEvent()
}
