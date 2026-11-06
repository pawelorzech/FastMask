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
import com.fastmask.domain.model.Accent
import com.fastmask.domain.repository.AppLockSetting
import com.fastmask.domain.repository.NotificationPromptSetting
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/**
 * A corrupt preferences file is unreadable for good once it happens (interrupted
 * write, full disk), and every read then throws. The handler trades the stored
 * settings for an app that still starts. It resets to "never set", so only keys
 * where that is safe may live here: accent, app lock, language, demo mode and
 * its tutorial flag, and the notification-prompt flag — all re-settable.
 *
 * Nothing whose default is the permissive side belongs in this file. The crash
 * reporting opt-out (default: enabled) has its own store for that reason; see
 * `com.fastmask.data.crash.crashReportingDataStore`.
 */
val Context.settingsDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "settings",
    corruptionHandler = ReplaceFileCorruptionHandler { emptyPreferences() },
)

@Singleton
class SettingsDataStore @Inject constructor(
    @ApplicationContext private val context: Context
) : AppLockSetting, NotificationPromptSetting {
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
    override suspend fun notificationPromptShown(): Boolean =
        context.settingsDataStore.data.first()[notificationPromptShownKey] ?: false

    override suspend fun setNotificationPromptShown(shown: Boolean) {
        context.settingsDataStore.edit { preferences ->
            preferences[notificationPromptShownKey] = shown
        }
    }
}
