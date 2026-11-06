package com.fastmask.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.fastmask.domain.crash.CrashReportingPreference
import com.fastmask.domain.model.Accent
import com.fastmask.domain.repository.AppLockSetting
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.runBlocking
import javax.inject.Inject
import javax.inject.Singleton

/**
 * A corrupt preferences file is unreadable for good once it happens (interrupted
 * write, full disk), and every read then throws. The handler trades the stored
 * settings — language, accent, app-lock flag, all re-settable — for an app that
 * still starts.
 */
val Context.settingsDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "settings",
    corruptionHandler = ReplaceFileCorruptionHandler { emptyPreferences() },
)

@Singleton
class SettingsDataStore @Inject constructor(
    @ApplicationContext private val context: Context
) : AppLockSetting {
    private val languageKey = stringPreferencesKey("language_code")

    val languageFlow: Flow<String?> = context.settingsDataStore.data.map { preferences ->
        preferences[languageKey]
    }

    suspend fun setLanguage(languageCode: String?) {
        context.settingsDataStore.edit { preferences ->
            if (languageCode == null) {
                preferences.remove(languageKey)
            } else {
                preferences[languageKey] = languageCode
            }
        }
    }

    // --- Pro personalization: accent theme + biometric app lock ---

    private val accentKey = stringPreferencesKey("accent")
    private val appLockKey = booleanPreferencesKey("app_lock_enabled")

    val accent: Flow<Accent> = context.settingsDataStore.data.map { preferences ->
        Accent.fromName(preferences[accentKey])
    }

    suspend fun setAccent(accent: Accent) {
        context.settingsDataStore.edit { preferences ->
            preferences[accentKey] = accent.name
        }
    }

    val appLockEnabled: Flow<Boolean> = context.settingsDataStore.data.map { preferences ->
        preferences[appLockKey] ?: false
    }

    suspend fun setAppLockEnabled(enabled: Boolean) {
        context.settingsDataStore.edit { preferences ->
            preferences[appLockKey] = enabled
        }
    }

    override suspend fun isAppLockEnabled(): Boolean = appLockEnabled.first()

    // --- POST_NOTIFICATIONS prompt (asked at most once) ---

    private val notificationPromptShownKey = booleanPreferencesKey("notification_prompt_shown")

    /**
     * True once the runtime notification prompt has been shown. Kept so the app
     * asks a single time: a second `launch()` on a permanently denied
     * permission returns instantly with no dialog, which would silently do
     * nothing on every launch.
     */
    suspend fun notificationPromptShown(): Boolean =
        context.settingsDataStore.data.first()[notificationPromptShownKey] ?: false

    suspend fun setNotificationPromptShown(shown: Boolean) {
        context.settingsDataStore.edit { preferences ->
            preferences[notificationPromptShownKey] = shown
        }
    }

    // --- Crash reporting (opt-out) ---

    private val crashReporting = CrashReportingSettings(context.settingsDataStore)

    /** Opt-out flag; `true` for every install that never touched the switch. */
    val crashReportingEnabled: Flow<Boolean> get() = crashReporting.enabled

    /**
     * The same preference with "never stored" and "could not be read" kept
     * apart. Startup uses this: a failed read must not be turned into a default
     * that re-enables collection for someone who opted out.
     */
    val crashReportingPreference: Flow<CrashReportingPreference> get() = crashReporting.preference

    /**
     * Synchronous seed for the settings switch. Without it the switch paints as
     * ON for the first frames of every entry into Settings — the opposite of
     * the truth for a user who opted out.
     *
     * It normally does no blocking at all: the value
     * comes from [CrashReportingSettings.lastKnown], which every launch fills
     * off the main thread when the startup pass reads the preference. The
     * `runBlocking` fallback covers only the window before that read lands —
     * reaching Settings that early is not a path the UI offers, and this is the
     * main thread, so it is a fallback rather than the normal cost. Degrades to
     * the documented default rather than throwing on the UI thread.
     */
    fun crashReportingEnabledBlocking(): Boolean =
        crashReporting.lastKnown?.enabledOrDefault
            ?: runCatching { runBlocking { crashReporting.enabled.first() } }
                .getOrDefault(CrashReportingSettings.DEFAULT_ENABLED)

    suspend fun setCrashReportingEnabled(enabled: Boolean) = crashReporting.setEnabled(enabled)
}
