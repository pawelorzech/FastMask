package com.fastmask.data.local

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import java.io.IOException

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
        get() = dataStore.data
            // A corrupted or unreadable preferences file surfaces here as an
            // IOException. Letting it through would kill the settings screen
            // over a diagnostics toggle, so it degrades to the documented
            // default; anything else (including CancellationException) is a
            // real bug and still propagates.
            .catch { throwable ->
                if (throwable is IOException) emit(emptyPreferences()) else throw throwable
            }
            .map { preferences ->
                // Read through asMap() rather than the get operator: Preferences.Key
                // equality is by name alone, so a value of the wrong type stored under
                // this name would make preferences[KEY] an unchecked cast whose
                // ClassCastException surfaces at an unpredictable point downstream.
                // An unreadable value is not an opt-out — the user never said no.
                val stored = preferences.asMap()[KEY]
                if (stored is Boolean) stored else DEFAULT_ENABLED
            }

    suspend fun setEnabled(enabled: Boolean) {
        // edit() touches this key only; replacing the whole Preferences object
        // would wipe the user's language, accent, app lock, mode and tutorial
        // flag. A failed write propagates: the caller decides how to degrade.
        dataStore.edit { preferences -> preferences[KEY] = enabled }
    }

    companion object {
        const val KEY_NAME = "crash_reporting_enabled"

        val KEY: Preferences.Key<Boolean> = booleanPreferencesKey(KEY_NAME)

        /** Opt-out: on unless the user said otherwise. */
        const val DEFAULT_ENABLED = true
    }
}
