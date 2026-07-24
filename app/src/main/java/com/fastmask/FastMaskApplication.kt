package com.fastmask

import android.app.Application
import android.util.Log
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import com.fastmask.data.local.SettingsDataStore
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

@HiltAndroidApp
class FastMaskApplication : Application() {

    override fun onCreate() {
        super.onCreate()
        restoreSavedLanguage()
    }

    private fun restoreSavedLanguage() {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        scope.launch {
            try {
                val savedLanguageCode = SettingsDataStore.getLanguageBlocking(this@FastMaskApplication)
                if (savedLanguageCode != null) {
                    val localeList = LocaleListCompat.forLanguageTags(savedLanguageCode)
                    AppCompatDelegate.setApplicationLocales(localeList)
                }
            } catch (e: Exception) {
                if (BuildConfig.DEBUG) {
                    Log.w("FastMask", "Failed to restore saved language", e)
                }
            }
        }
    }
}
