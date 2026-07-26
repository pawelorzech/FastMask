package com.fastmask.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.fastmask.domain.crash.CrashReportingPreference
import com.fastmask.domain.model.Accent
import com.fastmask.domain.model.AppMode
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.runBlocking
import javax.inject.Inject
import javax.inject.Singleton

val Context.settingsDataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

@Singleton
class SettingsDataStore @Inject constructor(
    @ApplicationContext private val context: Context
) {
    // Single definition, shared with the companion's static reader — the key
    // used to be declared twice with the same literal, so a rename on one side
    // would have silently orphaned the other's stored value.
    private val languageKey = LANGUAGE_KEY
    private val appModeKey = stringPreferencesKey("app_mode")
    private val tutorialCompletedKey = booleanPreferencesKey("tutorial_completed")

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

    fun getLanguageBlocking(): String? {
        return runBlocking {
            context.settingsDataStore.data.first()[languageKey]
        }
    }

    // --- App mode (REAL vs DEMO) ---

    val appMode: Flow<AppMode> = context.settingsDataStore.data.map { preferences ->
        preferences[appModeKey]?.let { value ->
            runCatching { AppMode.valueOf(value) }.getOrDefault(AppMode.REAL)
        } ?: AppMode.REAL
    }

    suspend fun setAppMode(mode: AppMode) {
        context.settingsDataStore.edit { preferences ->
            preferences[appModeKey] = mode.name
        }
    }

    /**
     * Synchronous getter used by [com.fastmask.MainActivity] when computing the start
     * destination and by [com.fastmask.data.repository.MaskedEmailRepositoryDispatcher] when
     * routing each call. Uses [runBlocking] on the DataStore data flow which resolves
     * quickly (in-memory cache after first read).
     */
    fun appModeBlocking(): AppMode {
        return runBlocking {
            val raw = context.settingsDataStore.data.first()[appModeKey]
            raw?.let { runCatching { AppMode.valueOf(it) }.getOrDefault(AppMode.REAL) }
                ?: AppMode.REAL
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

    // --- Tutorial completion flag ---

    val tutorialCompleted: Flow<Boolean> = context.settingsDataStore.data.map { preferences ->
        preferences[tutorialCompletedKey] ?: false
    }

    suspend fun setTutorialCompleted(done: Boolean) {
        context.settingsDataStore.edit { preferences ->
            preferences[tutorialCompletedKey] = done
        }
    }

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
     * Synchronous seed for the settings switch, mirroring [appModeBlocking].
     * Without it the switch paints as ON for the first frames of every entry
     * into Settings — the opposite of the truth for a user who opted out.
     * Degrades to the documented default rather than throwing on the UI thread.
     */
    fun crashReportingEnabledBlocking(): Boolean =
        runCatching { runBlocking { crashReporting.enabled.first() } }
            .getOrDefault(CrashReportingSettings.DEFAULT_ENABLED)

    suspend fun setCrashReportingEnabled(enabled: Boolean) = crashReporting.setEnabled(enabled)

    companion object {
        /**
         * Owns the language key for both readers: the injected instance above
         * and the static [getLanguageBlocking] below, which
         * [com.fastmask.FastMaskApplication] calls before the Hilt graph exists.
         */
        private val LANGUAGE_KEY = stringPreferencesKey("language_code")

        fun getLanguageBlocking(context: Context): String? {
            return runBlocking {
                context.settingsDataStore.data.first()[LANGUAGE_KEY]
            }
        }
    }
}
