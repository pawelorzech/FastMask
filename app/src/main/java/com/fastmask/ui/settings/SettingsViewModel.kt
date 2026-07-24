package com.fastmask.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fastmask.data.local.SettingsDataStore
import com.fastmask.domain.analytics.MonetizationAnalytics
import com.fastmask.domain.analytics.MonetizationEvent
import com.fastmask.domain.model.Accent
import com.fastmask.domain.model.AppMode
import com.fastmask.domain.model.Language
import com.fastmask.domain.model.ProStatus
import com.fastmask.domain.repository.ProRepository
import com.fastmask.domain.usecase.ExportMasksUseCase
import com.fastmask.domain.usecase.GetCurrentLanguageUseCase
import com.fastmask.domain.usecase.LogoutUseCase
import com.fastmask.domain.usecase.SetLanguageUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CoroutineExceptionHandler
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
class SettingsViewModel @Inject constructor(
    private val getCurrentLanguageUseCase: GetCurrentLanguageUseCase,
    private val setLanguageUseCase: SetLanguageUseCase,
    private val logoutUseCase: LogoutUseCase,
    private val settingsDataStore: SettingsDataStore,
    private val proRepository: ProRepository,
    private val exportMasksUseCase: ExportMasksUseCase,
    private val analytics: MonetizationAnalytics,
) : ViewModel() {

    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    // Channel-backed one-time events: buffered delivery survives windows with
    // no active collector (e.g. mid-rotation) and each event is handled once.
    private val _events = Channel<SettingsEvent>(Channel.BUFFERED)
    val events: Flow<SettingsEvent> = _events.receiveAsFlow()

    // A DataStore/token write can throw (disk full, KeyStore corruption).
    // viewModelScope rethrows uncaught exceptions on the main thread → crash;
    // this swallows them so a failed write degrades quietly. When it aborts the
    // coroutine before a post-write navigation event (logout, exitDemoMode), the
    // event is simply not sent — the user stays put rather than navigating on a
    // half-completed state change.
    private val writeErrorHandler = CoroutineExceptionHandler { _, _ -> }

    /** Live app-mode flag — used to render the demo "sign in" section. */
    val appMode: StateFlow<AppMode> = settingsDataStore.appMode.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = settingsDataStore.appModeBlocking(),
    )

    val proStatus: StateFlow<ProStatus> = proRepository.proStatus

    val accent: StateFlow<Accent> = settingsDataStore.accent.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = Accent.DEFAULT,
    )

    val appLockEnabled: StateFlow<Boolean> = settingsDataStore.appLockEnabled.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = false,
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
        viewModelScope.launch(writeErrorHandler) {
            setLanguageUseCase(language)
            _uiState.update { it.copy(selectedLanguage = language) }
        }
    }

    fun logout() {
        viewModelScope.launch(writeErrorHandler) {
            logoutUseCase()
            _events.send(SettingsEvent.LoggedOut)
        }
    }

    /**
     * Leave demo mode without nuking auth state — flip the mode flag, reset
     * the tutorial flag (so a future demo entry shows it again), then emit
     * an event the screen turns into navigation to the login flow.
     */
    fun exitDemoMode() {
        viewModelScope.launch(writeErrorHandler) {
            settingsDataStore.setAppMode(AppMode.REAL)
            settingsDataStore.setTutorialCompleted(false)
            _events.send(SettingsEvent.GoToSignIn)
        }
    }

    // --- FastMask Pro ---

    fun onProRowClick() {
        viewModelScope.launch { _events.send(SettingsEvent.OpenPro(source = "settings")) }
    }

    fun onAccentClick() {
        if (proStatus.value.isPro) {
            _uiState.update { it.copy(showAccentDialog = true) }
        } else {
            analytics.track(MonetizationEvent.PREMIUM_FEATURE_TAPPED, source = "accent")
            viewModelScope.launch { _events.send(SettingsEvent.OpenPro(source = "accent")) }
        }
    }

    fun onAccentDialogDismissed() {
        _uiState.update { it.copy(showAccentDialog = false) }
    }

    fun onAccentSelected(accent: Accent) {
        _uiState.update { it.copy(showAccentDialog = false) }
        if (!proStatus.value.isPro) return
        viewModelScope.launch(writeErrorHandler) { settingsDataStore.setAccent(accent) }
    }

    /**
     * Turning the lock ON requires Pro (and a securable device — the screen
     * checks that first); turning it OFF is always allowed so a user who loses
     * Pro is never locked out.
     */
    fun onAppLockToggled(enabled: Boolean) {
        if (enabled && !proStatus.value.isPro) {
            analytics.track(MonetizationEvent.PREMIUM_FEATURE_TAPPED, source = "app_lock")
            viewModelScope.launch { _events.send(SettingsEvent.OpenPro(source = "app_lock")) }
            return
        }
        viewModelScope.launch(writeErrorHandler) { settingsDataStore.setAppLockEnabled(enabled) }
    }

    fun onExportClick() {
        if (!proStatus.value.isPro) {
            analytics.track(MonetizationEvent.PREMIUM_FEATURE_TAPPED, source = "export")
            viewModelScope.launch { _events.send(SettingsEvent.OpenPro(source = "export")) }
            return
        }
        // In UI state, not a private var: the export does a network fetch of
        // every mask, and the row must show progress instead of looking dead.
        if (_uiState.value.exportInFlight) return
        _uiState.update { it.copy(exportInFlight = true) }
        viewModelScope.launch {
            exportMasksUseCase()
                .onSuccess { csv -> _events.send(SettingsEvent.ShareCsv(csv)) }
                .onFailure { _events.send(SettingsEvent.ExportFailed) }
            _uiState.update { it.copy(exportInFlight = false) }
        }
    }
}

data class SettingsUiState(
    val selectedLanguage: Language? = null,
    val showAccentDialog: Boolean = false,
    val exportInFlight: Boolean = false,
)

sealed class SettingsEvent {
    data object LoggedOut : SettingsEvent()
    data object GoToSignIn : SettingsEvent()
    data class OpenPro(val source: String) : SettingsEvent()
    data class ShareCsv(val csv: String) : SettingsEvent()
    data object ExportFailed : SettingsEvent()
}
