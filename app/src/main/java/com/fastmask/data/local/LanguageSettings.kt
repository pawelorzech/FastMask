package com.fastmask.data.local

import android.content.Context
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.fastmask.domain.model.Language
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LanguageSettings @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val languageKey = stringPreferencesKey("language_code")

    val language: Flow<Language?> = context.settingsDataStore.data.map { preferences ->
        Language.fromCode(preferences[languageKey])
    }

    suspend fun set(language: Language?) {
        val code = language?.code
        context.settingsDataStore.edit { preferences ->
            if (code == null) {
                preferences.remove(languageKey)
            } else {
                preferences[languageKey] = code
            }
        }
        AppCompatDelegate.setApplicationLocales(
            if (code != null) LocaleListCompat.forLanguageTags(code) else LocaleListCompat.getEmptyLocaleList()
        )
    }

    suspend fun restore() {
        val code = context.settingsDataStore.data.map { it[languageKey] }.first() ?: return
        val localeList = LocaleListCompat.forLanguageTags(code)
        withContext(Dispatchers.Main.immediate) {
            if (AppCompatDelegate.getApplicationLocales() != localeList) {
                AppCompatDelegate.setApplicationLocales(localeList)
            }
        }
    }
}
