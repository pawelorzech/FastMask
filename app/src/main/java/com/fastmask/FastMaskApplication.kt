package com.fastmask

import android.app.Application
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import com.fastmask.data.local.SettingsDataStore
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class FastMaskApplication : Application() {

    override fun onCreate() {
        super.onCreate()
        // Restore saved language after super.onCreate() but before any Activity starts.
        // We read directly from DataStore here since Hilt injection happens during super.onCreate().
        restoreSavedLanguage()
    }

    private fun restoreSavedLanguage() {
        try {
            val savedLanguageCode = SettingsDataStore.getLanguageBlocking(this)
            if (savedLanguageCode != null) {
                val localeList = LocaleListCompat.forLanguageTags(savedLanguageCode)
                AppCompatDelegate.setApplicationLocales(localeList)
            }
        } catch (e: Exception) {
            // If reading fails, use system default locale
        }
    }
}
