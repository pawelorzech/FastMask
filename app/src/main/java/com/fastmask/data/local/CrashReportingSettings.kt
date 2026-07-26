package com.fastmask.data.local

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import com.fastmask.domain.crash.CrashReportingPolicy
import com.fastmask.domain.crash.CrashReportingPreference
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
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

    /**
     * The stored preference, with "no key" and "could not read" kept apart.
     *
     * Collapsing the two is what let a transient DataStore failure re-enable
     * collection for a user who had opted out: absence of the key means a fresh
     * or upgraded install (opted in by default), while a failed read means the
     * user's choice is simply unknown. [CrashReportingStartup] is the consumer
     * that cares — it applies the first and skips the SDK entirely on the second.
     */
    /**
     * The last definite answer this store produced, for callers that need a
     * value synchronously.
     *
     * It exists so [SettingsDataStore.crashReportingEnabledBlocking] can seed
     * the settings switch without blocking the main thread on I/O. Startup
     * reads the preference off the main thread on every launch, so by the time
     * anyone can open Settings this is populated and `runBlocking` is skipped
     * entirely; the blocking read stays as the fallback for the window before
     * that first read lands.
     *
     * [CrashReportingPreference.Unreadable] is deliberately not memoised: it
     * means "we do not know", and caching it would freeze a transient failure
     * into every later read.
     */
    @Volatile
    var lastKnown: CrashReportingPreference? = null
        private set

    val preference: Flow<CrashReportingPreference>
        get() = dataStore.data
            .map { preferences ->
                // Read through asMap() rather than the get operator: Preferences.Key
                // equality is by name alone, so a value of the wrong type stored under
                // this name would make preferences[KEY] an unchecked cast whose
                // ClassCastException surfaces at an unpredictable point downstream.
                when (val stored = preferences.asMap()[KEY]) {
                    null -> CrashReportingPreference.Missing
                    is Boolean -> CrashReportingPreference.Stored(stored)
                    // A value we did not write, under our name. It says nothing
                    // about what the user chose, so it is treated as unreadable
                    // rather than as an opt-in.
                    else -> CrashReportingPreference.Unreadable
                }
            }
            // A corrupted or unreadable preferences file surfaces as an
            // IOException on the data flow. Letting it through would kill the
            // settings screen over a diagnostics toggle, so it degrades to
            // Unreadable; anything else (including CancellationException) is a
            // real bug and still propagates.
            .catch { throwable ->
                if (throwable is IOException) {
                    emit(CrashReportingPreference.Unreadable)
                } else {
                    throw throwable
                }
            }
            .onEach { resolved ->
                if (resolved != CrashReportingPreference.Unreadable) {
                    lastKnown = resolved
                }
            }

    /**
     * The preference as a plain flag, for rendering the switch. Anything that
     * drives the SDK must use [preference] instead — see the KDoc on
     * [CrashReportingPreference.enabledOrDefault].
     */
    val enabled: Flow<Boolean>
        get() = preference.map { it.enabledOrDefault }

    suspend fun setEnabled(enabled: Boolean) {
        // edit() touches this key only; replacing the whole Preferences object
        // would wipe the user's language, accent, app lock, mode and tutorial
        // flag. A failed write propagates: the caller decides how to degrade.
        dataStore.edit { preferences -> preferences[KEY] = enabled }
    }

    companion object {
        const val KEY_NAME = "crash_reporting_enabled"

        val KEY: Preferences.Key<Boolean> = booleanPreferencesKey(KEY_NAME)

        /** Opt-out: on unless the user said otherwise. Owned by the domain policy. */
        const val DEFAULT_ENABLED = CrashReportingPolicy.DEFAULT_ENABLED
    }
}
