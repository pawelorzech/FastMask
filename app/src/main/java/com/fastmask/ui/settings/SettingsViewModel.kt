package com.fastmask.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fastmask.data.local.SettingsDataStore
import com.fastmask.domain.model.AppMode
import com.fastmask.domain.model.Language
import com.fastmask.domain.usecase.GetCurrentLanguageUseCase
import com.fastmask.domain.usecase.LogoutUseCase
import com.fastmask.domain.usecase.SetLanguageUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val getCurrentLanguageUseCase: GetCurrentLanguageUseCase,
    private val setLanguageUseCase: SetLanguageUseCase,
    private val logoutUseCase: LogoutUseCase,
    private val settingsDataStore: SettingsDataStore,
) : ViewModel() {

    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    private val _events = MutableSharedFlow<SettingsEvent>()
    val events: SharedFlow<SettingsEvent> = _events.asSharedFlow()

    /** Live app-mode flag — used to render the demo "sign in" section. */
    val appMode: StateFlow<AppMode> = settingsDataStore.appMode.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = settingsDataStore.appModeBlocking(),
    )

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

    /**
     * Leave demo mode without nuking auth state — flip the mode flag, reset
     * the tutorial flag (so a future demo entry shows it again), then emit
     * an event the screen turns into navigation to the login flow.
     */
    fun exitDemoMode() {
        viewModelScope.launch {
            settingsDataStore.setAppMode(AppMode.REAL)
            settingsDataStore.setTutorialCompleted(false)
            _events.emit(SettingsEvent.GoToSignIn)
        }
    }
}

data class SettingsUiState(
    val selectedLanguage: Language? = null
)

sealed class SettingsEvent {
    data object LoggedOut : SettingsEvent()
    data object GoToSignIn : SettingsEvent()
}
