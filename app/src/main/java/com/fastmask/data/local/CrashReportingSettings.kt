package com.fastmask.data.local

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import kotlinx.coroutines.flow.Flow

/**
 * The `crash_reporting_enabled` preference, split out of [SettingsDataStore] so
 * it can be exercised against a plain `DataStore<Preferences>` in unit tests.
 *
 * Opt-out semantics: absence of the key means ENABLED. Every installation that
 * updates to the version introducing this key has no value stored, so the
 * default is what those users get.
 */
class CrashReportingSettings(
    private val dataStore: DataStore<Preferences>,
) {

    val enabled: Flow<Boolean>
        get() = TODO("stub — implemented by the crash reporting change")

    suspend fun setEnabled(enabled: Boolean): Unit =
        TODO("stub — implemented by the crash reporting change")

    companion object {
        const val KEY_NAME = "crash_reporting_enabled"

        val KEY: Preferences.Key<Boolean> = booleanPreferencesKey(KEY_NAME)

        /** Opt-out: on unless the user said otherwise. */
        const val DEFAULT_ENABLED = true
    }
}
