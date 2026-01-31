package com.fastmask.domain.usecase

import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import com.fastmask.data.local.SettingsDataStore
import com.fastmask.domain.model.Language
import javax.inject.Inject

class SetLanguageUseCase @Inject constructor(
    private val settingsDataStore: SettingsDataStore
) {
    suspend operator fun invoke(language: Language?) {
        val languageCode = language?.code
        settingsDataStore.setLanguage(languageCode)

        val localeList = if (languageCode != null) {
            LocaleListCompat.forLanguageTags(languageCode)
        } else {
            LocaleListCompat.getEmptyLocaleList()
        }
        AppCompatDelegate.setApplicationLocales(localeList)
    }
}
