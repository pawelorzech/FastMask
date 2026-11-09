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
import com.fastmask.core.IoDispatcher
import com.fastmask.core.QuietWriteFailure
import com.fastmask.ui.common.UiErrors
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CoroutineDispatcher
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
    // Must be the injected singleton: the sign-out guard is per-instance state,
    // so a second instance would never see the clear.
    private val exportCache: ExportCache,
    @param:IoDispatcher private val ioDispatcher: CoroutineDispatcher,
    private val analytics: MonetizationAnalytics,
    private val crashReporting: CrashReporting,
) : ViewModel() {

    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    // Channel-backed one-time events: buffered delivery survives windows with
    // no active collector (e.g. mid-rotation) and each event is handled once.
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

    // --- Crash reporting (opt-out) ---

    /**
     * Seeded from storage, not from the opt-out default, so a user who opted
     * out never sees the switch ON for the first frames. A read failure falls
     * back to the default.
     */
    val crashReportingEnabled: StateFlow<Boolean> =
        crashReporting.enabled.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = crashReporting.enabledBlocking(),
        )

    /**
     * [CrashReporting.setEnabled] applies the SDK before persisting (so a failed
     * write still stops collection for this session) and survives an SDK that
     * cannot start; `QuietWriteFailure` keeps a failed write from crashing.
     */
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

    // --- FastMask Pro ---

    fun onProRowClick() {
        viewModelScope.launch { _events.send(SettingsEvent.OpenPro(source = PRO_SOURCE_SETTINGS)) }
    }

    /** True with Pro; otherwise records the tap on [feature] and opens the paywall. */
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

    /**
     * Turning the lock ON requires Pro (and a securable device — the screen
     * checks that first); turning it OFF is always allowed so a user who loses
     * Pro is never locked out.
     */
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
            // The cache owns the order (generation, fetch, write) that keeps an export
            // whose fetch outlived a sign-out off the disk.
            val outcome = exportCache.export(ioDispatcher) { maskedEmailRepository.getMaskedEmails() }
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

    /** The share intent for an exported file (see [ExportCache.shareIntent]). */
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

    /** @param messageRes the localized reason, already resolved by [UiErrors]. */
    data class ExportFailed(@StringRes val messageRes: Int) : SettingsEvent()
}
