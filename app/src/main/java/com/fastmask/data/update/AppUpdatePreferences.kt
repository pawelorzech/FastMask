package com.fastmask.data.update

import android.content.Context
import android.content.SharedPreferences
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Nothing here is ever read by anything that reports anywhere. This value exists so the app can
 * stop asking, and turning it into a signal that leaves the device would make the "crash
 * diagnostics only" promise untrue.
 */
@Singleton
class AppUpdatePreferences @Inject constructor(@ApplicationContext context: Context) {

    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    /**
     * A version rather than a flag, so one "not now" silences only that release.
     */
    var dismissedVersionCode: Int
        get() = prefs.getInt(KEY_DISMISSED_VERSION, AppUpdatePolicy.NO_VERSION)
        set(value) {
            prefs.edit().putInt(KEY_DISMISSED_VERSION, value).apply()
        }

    private companion object {
        const val PREFS_NAME = "app_update_prefs"
        const val KEY_DISMISSED_VERSION = "dismissed_update_version"
    }
}
