package com.fastmask.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
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
    private val languageKey = stringPreferencesKey("language_code")
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

    // --- Tutorial completion flag ---

    val tutorialCompleted: Flow<Boolean> = context.settingsDataStore.data.map { preferences ->
        preferences[tutorialCompletedKey] ?: false
    }

    suspend fun setTutorialCompleted(done: Boolean) {
        context.settingsDataStore.edit { preferences ->
            preferences[tutorialCompletedKey] = done
        }
    }

    companion object {
        private val LANGUAGE_KEY = stringPreferencesKey("language_code")

        fun getLanguageBlocking(context: Context): String? {
            return runBlocking {
                context.settingsDataStore.data.first()[LANGUAGE_KEY]
            }
        }
    }
}
