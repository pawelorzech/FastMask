package com.fastmask

import android.app.Application
import android.util.Log
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import com.fastmask.data.local.CrashReportingSettings
import com.fastmask.data.local.SettingsDataStore
import com.fastmask.domain.crash.CrashReportingController
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltAndroidApp
class FastMaskApplication : Application() {
    private val startupScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    @Inject
    lateinit var crashReporting: CrashReportingController

    @Inject
    lateinit var settingsDataStore: SettingsDataStore

    override fun onCreate() {
        super.onCreate()
        applyCrashReportingPreference()
        restoreSavedLanguage()
    }

    /**
     * Crashlytics auto-initialises with collection on, so the stored opt-out has
     * to be re-applied on every start — otherwise a user who turned it off keeps
     * being reported on until they open Settings again. Debug builds are gated
     * separately inside the controller.
     */
    private fun applyCrashReportingPreference() {
        startupScope.launch {
            // An unreadable preference falls back to the documented default
            // rather than leaving Crashlytics in whatever state it initialised
            // itself to. This must never take down app start.
            val enabled = try {
                settingsDataStore.crashReportingEnabled.first()
            } catch (e: Exception) {
                if (BuildConfig.DEBUG) {
                    Log.w("FastMask", "Failed to read the crash reporting preference", e)
                }
                CrashReportingSettings.DEFAULT_ENABLED
            }
            crashReporting.apply(enabled)
        }
    }

    private fun restoreSavedLanguage() {
        startupScope.launch {
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
