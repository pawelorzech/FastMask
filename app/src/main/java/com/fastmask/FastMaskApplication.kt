package com.fastmask

import android.app.Application
import android.util.Log
import com.fastmask.core.runSuspendCatching
import com.fastmask.data.crash.CrashReporting
import com.fastmask.data.local.ExportCache
import com.fastmask.data.local.LanguageSettings
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltAndroidApp
class FastMaskApplication : Application() {

    /**
     * A SupervisorJob alone does not stop an uncaught exception from reaching
     * the thread's default handler, which kills the process — during
     * `onCreate`, so before the app draws anything. The handler is what
     * actually makes these startup jobs non-fatal.
     */
    private val startupErrorHandler = CoroutineExceptionHandler { _, error ->
        if (BuildConfig.DEBUG) {
            Log.w("FastMask", "Startup task failed", error)
        }
    }

    private val startupScope =
        CoroutineScope(SupervisorJob() + Dispatchers.IO + startupErrorHandler)

    @Inject
    lateinit var crashReporting: CrashReporting

    @Inject
    lateinit var exportCache: ExportCache

    @Inject
    lateinit var languageSettings: LanguageSettings

    override fun onCreate() {
        super.onCreate()
        applyCrashReportingPreference()
        restoreSavedLanguage()
        pruneExpiredExports()
    }

    /**
     * The CSV export is the only unencrypted copy of the user's full mask list on
     * disk and must age out after an hour; a cold start is an independent trigger
     * that costs one directory listing.
     */
    private fun pruneExpiredExports() {
        startupScope.launch {
            exportCache.pruneExpired()
        }
    }

    /**
     * Crashlytics auto-initialises with collection on, so the stored opt-out has
     * to be re-applied on every start — otherwise a user who turned it off keeps
     * being reported on until they open Settings again. Debug builds are gated
     * separately inside the controller.
     *
     * Everything that can fail — the DataStore read *and* the SDK call — lives
     * inside [CrashReporting.applyAtStartup], which is contractually non-throwing and
     * unit-tested as such; the handler on [startupScope] is the second line of
     * defence.
     */
    private fun applyCrashReportingPreference() {
        startupScope.launch {
            crashReporting.applyAtStartup()
        }
    }

    private fun restoreSavedLanguage() {
        startupScope.launch {
            runSuspendCatching { languageSettings.restore() }.onFailure { e ->
                if (BuildConfig.DEBUG) {
                    Log.w("FastMask", "Failed to restore saved language", e)
                }
            }
        }
    }
}
