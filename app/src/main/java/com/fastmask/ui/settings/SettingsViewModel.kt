package com.fastmask.ui.settings

import android.content.Intent
import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fastmask.R
import com.fastmask.data.crash.CrashReporting
import com.fastmask.data.local.ExportCache
import com.fastmask.data.local.ExportOutcome
import com.fastmask.data.local.LanguageSettings
import com.fastmask.data.local.SettingsDataStore
import com.fastmask.domain.analytics.MonetizationAnalytics
import com.fastmask.domain.analytics.MonetizationEvent
import com.fastmask.domain.model.Accent
import com.fastmask.domain.model.Language
import com.fastmask.domain.model.ProFeature
import com.fastmask.ui.pro.PRO_SOURCE_SETTINGS
import com.fastmask.domain.model.ProStatus
import com.fastmask.domain.model.Session
import com.fastmask.domain.repository.ProRepository
import com.fastmask.domain.repository.MaskedEmailRepository
import com.fastmask.domain.repository.SessionRepository
import com.fastmask.core.QuietWriteFailure
import com.fastmask.ui.common.UiErrors
import dagger.hilt.android.lifecycle.HiltViewModel
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
import java.io.File
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val languageSettings: LanguageSettings,
    private val sessionRepository: SessionRepository,
    private val settingsDataStore: SettingsDataStore,
    private val proRepository: ProRepository,
    private val maskedEmailRepository: MaskedEmailRepository,
    private val exportCache: ExportCache,
    private val analytics: MonetizationAnalytics,
    private val crashReporting: CrashReporting,
) : ViewModel() {

    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    private val _events = Channel<SettingsEvent>(Channel.BUFFERED)
    val events: Flow<SettingsEvent> = _events.receiveAsFlow()

    val session: StateFlow<Session> = sessionRepository.session

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

    /** Seeded from storage, not the opt-out default, so an opted-out user never sees the switch ON for the first frames. */
    val crashReportingEnabled: StateFlow<Boolean> =
        crashReporting.enabled.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = crashReporting.enabledBlocking(),
        )

    fun onCrashReportingToggled(enabled: Boolean) {
        viewModelScope.launch(QuietWriteFailure) {
            crashReporting.setEnabled(enabled)
        }
    }

    init {
        loadCurrentLanguage()
    }

    private fun loadCurrentLanguage() {
        viewModelScope.launch {
            languageSettings.language.collect { language ->
                _uiState.update { it.copy(selectedLanguage = language) }
            }
        }
    }

    fun onLanguageSelected(language: Language?) {
        viewModelScope.launch(QuietWriteFailure) {
            languageSettings.set(language)
            _uiState.update { it.copy(selectedLanguage = language) }
        }
    }

    fun logout() {
        viewModelScope.launch(QuietWriteFailure) {
            sessionRepository.signOut()
            _events.send(SettingsEvent.LoggedOut)
        }
    }

    fun exitDemoMode() {
        viewModelScope.launch(QuietWriteFailure) {
            sessionRepository.signOut()
            _events.send(SettingsEvent.GoToSignIn)
        }
    }

    fun onProRowClick() {
        viewModelScope.launch { _events.send(SettingsEvent.OpenPro(source = PRO_SOURCE_SETTINGS)) }
    }

    private fun hasPro(feature: ProFeature): Boolean {
        if (proStatus.value.isPro) return true
        analytics.track(MonetizationEvent.PREMIUM_FEATURE_TAPPED, source = feature.source)
        viewModelScope.launch { _events.send(SettingsEvent.OpenPro(source = feature.source)) }
        return false
    }

    fun onAccentClick() {
        hasPro(ProFeature.ACCENT)
    }

    fun onAccentSelected(accent: Accent) {
        // The picker locks Pro seeds, so a tap on one never reaches here without Pro.
        if (accent.isLockedFor(proStatus.value.isPro)) return
        viewModelScope.launch(QuietWriteFailure) { settingsDataStore.setAccent(accent) }
    }

    fun onAppLockToggled(enabled: Boolean) {
        if (enabled && !hasPro(ProFeature.APP_LOCK)) return
        viewModelScope.launch(QuietWriteFailure) { settingsDataStore.setAppLockEnabled(enabled) }
    }

    fun onExportClick() {
        if (!hasPro(ProFeature.EXPORT)) return
        if (_uiState.value.exportInFlight) return
        _uiState.update { it.copy(showExportConfirmation = true) }
    }

    fun onExportConfirmationDismissed() {
        _uiState.update { it.copy(showExportConfirmation = false) }
    }

    fun onExportConfirmed() {
        if (!_uiState.value.showExportConfirmation) return
        _uiState.update { it.copy(showExportConfirmation = false) }

        // Entitlement can change while the confirmation dialog is open. Do not
        // let a stale Pro snapshot bypass the same gate as the original tap.
        if (!hasPro(ProFeature.EXPORT)) return

        if (_uiState.value.exportInFlight) return
        _uiState.update { it.copy(exportInFlight = true) }
        viewModelScope.launch {
            val outcome = exportCache.export { maskedEmailRepository.getMaskedEmails() }
            when (outcome) {
                is ExportOutcome.Written -> _events.send(SettingsEvent.ShareCsv(outcome.file))
                ExportOutcome.WriteFailed -> _events.send(
                    SettingsEvent.ExportFailed(R.string.settings_export_failed_write)
                )
                is ExportOutcome.FetchFailed -> _events.send(
                    SettingsEvent.ExportFailed(
                        UiErrors.messageRes(outcome.error, R.string.settings_export_failed)
                    )
                )
            }
            _uiState.update { it.copy(exportInFlight = false) }
        }
    }

    fun shareIntent(file: File): Intent = exportCache.shareIntent(file)
}

data class SettingsUiState(
    val selectedLanguage: Language? = null,
    val showExportConfirmation: Boolean = false,
    val exportInFlight: Boolean = false,
)

sealed class SettingsEvent {
    data object LoggedOut : SettingsEvent()
    data object GoToSignIn : SettingsEvent()
    data class OpenPro(val source: String) : SettingsEvent()
    data class ShareCsv(val file: File) : SettingsEvent()

    data class ExportFailed(@StringRes val messageRes: Int) : SettingsEvent()
}
