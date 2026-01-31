package com.fastmask.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
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

    companion object {
        private val LANGUAGE_KEY = stringPreferencesKey("language_code")

        fun getLanguageBlocking(context: Context): String? {
            return runBlocking {
                context.settingsDataStore.data.first()[LANGUAGE_KEY]
            }
        }
    }
}
