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
 * where that is safe may live here. Nothing whose default is the permissive side
 * belongs in this file.
 */
val Context.settingsDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "settings",
    corruptionHandler = ReplaceFileCorruptionHandler { emptyPreferences() },
)

@Singleton
class SettingsDataStore @Inject constructor(
    @ApplicationContext private val context: Context
) : NotificationPromptSetting {

    private val accentKey = stringPreferencesKey("accent")

    val accent: Flow<Accent> = context.settingsDataStore.data.map { preferences ->
        Accent.fromName(preferences[accentKey])
    }

    suspend fun setAccent(accent: Accent) {
        context.settingsDataStore.edit { preferences ->
            preferences[accentKey] = accent.name
        }
    }

    private val notificationPromptShownKey = booleanPreferencesKey("notification_prompt_shown")

    // A second `launch()` on a permanently denied permission returns instantly
    // with no dialog, so the app must ask only once.
    override suspend fun notificationPromptShown(): Boolean =
        context.settingsDataStore.data.first()[notificationPromptShownKey] ?: false

    override suspend fun setNotificationPromptShown(shown: Boolean) {
        context.settingsDataStore.edit { preferences ->
            preferences[notificationPromptShownKey] = shown
        }
    }
}
