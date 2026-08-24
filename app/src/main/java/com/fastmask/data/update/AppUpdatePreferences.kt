package com.fastmask.data.update

import android.content.Context
import android.content.SharedPreferences

/**
 * The one thing the update prompt has to remember between launches: which version the user said no
 * to. Plain SharedPreferences — a version code is neither a secret nor account data, so it has no
 * business in [com.fastmask.data.local.TokenStorage].
 *
 * Nothing here is ever read by anything that reports anywhere. This value exists so the app can
 * stop asking, and turning it into a signal that leaves the device would make the "crash
 * diagnostics only" promise untrue.
 */
class AppUpdatePreferences(context: Context) {

    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    /**
     * The versionCode of the flexible update the user declined, or [AppUpdatePolicy.NO_VERSION].
     *
     * Storing the version rather than a flag is what keeps a refusal narrow: it silences exactly
     * the release that was turned down, and the next one asks again. A boolean would have meant
     * one "not now" opting the user out of every future update.
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
