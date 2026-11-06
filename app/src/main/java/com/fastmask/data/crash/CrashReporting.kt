package com.fastmask.data.crash

import androidx.datastore.core.DataStore
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.preferencesOf
import com.fastmask.domain.crash.CrashReporter
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.runBlocking
import java.io.IOException

/**
 * The one place that knows about the crash reporting opt-out: the stored
 * preference, the policy and how both are applied to the SDK behind
 * [CrashReporter].
 *
 * **Preference.** `crash_reporting_enabled` in a DataStore of its own
 * (`crash_reporting`, see [crashReportingDataStore]), owned by this class alone.
 * Opt-out: no key means ENABLED, which is what a fresh install and every
 * installation updating into the version that introduced the key get. The key
 * name is a contract with already-installed copies; renaming it silently
 * re-enables collection for everyone who opted out. A value written by the
 * older versions in the shared `settings` store is moved over once by
 * [CrashOptOutMigration]. The store is separate because "no key" means ENABLED:
 * the shared store resets to "no key" on corruption, which would have turned
 * collection back on; this one resets to an explicit opt-out instead.
 *
 * **Policy.** Debug builds never collect, whatever the user says, so a
 * development crash cannot reach the production console.
 *
 * **Unreadable is not a decision.** "No key" (default on) and "the store could
 * not be read" are kept apart internally. A failed read says nothing about what
 * the user chose, and Crashlytics persists its own collection flag, so at
 * startup an unreadable preference leaves the SDK alone. Collapsing the two
 * would let a transient DataStore failure re-enable collection for someone who
 * had opted out.
 *
 * **Deferral.** [reporter] is a provider, never resolved in the constructor.
 * This object is injected into `FastMaskApplication` on the main thread, before
 * `super.onCreate()`; building it touches neither DataStore I/O nor Firebase.
 */
class CrashReporting(
    private val dataStore: DataStore<Preferences>,
    private val reporter: () -> CrashReporter,
    private val isDebugBuild: Boolean,
    private val onFailure: (Throwable) -> Unit = {},
) {

    private sealed interface Stored {
        data class Value(val enabled: Boolean) : Stored
        data object Missing : Stored
        data object Unreadable : Stored
    }

    private val Stored.enabledOrDefault: Boolean
        get() = if (this is Stored.Value) enabled else DEFAULT_ENABLED

    /**
     * The last definite answer the store produced, so the Settings switch can be
     * seeded synchronously without blocking the main thread on I/O. Startup reads
     * the preference off the main thread on every launch, so this is normally
     * populated before anyone can open Settings. [Stored.Unreadable] is never
     * memoised: it means "unknown", and caching it would freeze a transient
     * failure into every later read.
     */
    @Volatile
    private var lastKnown: Stored? = null

    private val stored: Flow<Stored>
        get() = dataStore.data
            .map { preferences ->
                // asMap() rather than the get operator: Preferences.Key equality is
                // by name alone, so a value of the wrong type under this name would
                // make preferences[KEY] an unchecked cast whose ClassCastException
                // surfaces at an unpredictable point downstream.
                when (val value = preferences.asMap()[KEY]) {
                    null -> Stored.Missing
                    is Boolean -> Stored.Value(value)
                    // A value we did not write says nothing about the user's choice.
                    else -> Stored.Unreadable
                }
            }
            // A corrupt or unreadable file surfaces as an IOException on the data
            // flow. Anything else (including cancellation) is a real bug.
            .catch { throwable ->
                if (throwable is IOException) emit(Stored.Unreadable) else throw throwable
            }
            .onEach { if (it != Stored.Unreadable) lastKnown = it }

    /**
     * The preference as a flag for rendering the switch ("on" when unknown, the
     * honest default). Never use it to drive the SDK.
     */
    val enabled: Flow<Boolean>
        get() = stored.map { it.enabledOrDefault }

    /**
     * Synchronous seed for the Settings switch, so it never paints ON for an
     * opted-out user. Normally no blocking at all (see [lastKnown]); the
     * `runBlocking` fallback covers only the window before the first startup
     * read lands. Degrades to the default rather than throwing on the UI thread.
     */
    fun enabledBlocking(): Boolean =
        lastKnown?.enabledOrDefault
            ?: runCatching { runBlocking { enabled.first() } }.getOrDefault(DEFAULT_ENABLED)

    /**
     * Re-applies the stored preference to the SDK. Crashlytics auto-initialises
     * with collection on, so this has to run on every start.
     *
     * Never throws except [CancellationException]: it runs from
     * `Application.onCreate`, where an escaping exception kills the process
     * before the first frame, and the SDK resolves itself lazily on the first
     * call (it throws `IllegalStateException` where `FirebaseInitProvider` never
     * ran). An unreadable preference leaves the SDK untouched.
     */
    suspend fun applyAtStartup() {
        val current = try {
            stored.first()
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (error: Throwable) {
            onFailure(error)
            Stored.Unreadable
        }
        if (current == Stored.Unreadable) return

        try {
            applyToSdk(current.enabledOrDefault)
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (error: Throwable) {
            onFailure(error)
        }
    }

    /**
     * The Settings switch. Applies the SDK first and persists second, on purpose:
     * the SDK call is synchronous, so a write that fails (full disk) still leaves
     * collection stopped for this session instead of turning the opt-out into a
     * silent no-op. An SDK that cannot start is reported and swallowed, and the
     * preference is still written, so the next launch re-applies the choice.
     * A failed write propagates for the caller to handle.
     */
    suspend fun setEnabled(enabled: Boolean) {
        try {
            applyToSdk(enabled)
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (error: Throwable) {
            onFailure(error)
        }
        // edit() touches this key only; replacing the whole Preferences object
        // would wipe every other setting.
        dataStore.edit { preferences -> preferences[KEY] = enabled }
        lastKnown = Stored.Value(enabled)
    }

    /**
     * Firebase persists a runtime opt-out immediately but applies `false` on the
     * next app run, so the privacy policy must not claim the current process
     * stops instantly. Collection is switched off *before* queued reports are
     * dropped; the other way round, a crash landing in between is written back
     * after the purge. Deletion follows the preference, not the policy result:
     * a debug build collects nothing, but an explicit opt-out still has to drop
     * whatever an earlier release build left queued.
     */
    private fun applyToSdk(userEnabled: Boolean) {
        val sdk = reporter()
        sdk.setCollectionEnabled(!isDebugBuild && userEnabled)
        if (!userEnabled) sdk.deleteUnsentReports()
    }

    companion object {
        const val KEY_NAME = "crash_reporting_enabled"
        internal val KEY = booleanPreferencesKey(KEY_NAME)

        /**
         * What a corrupt store becomes: an explicit opt-out. An empty store would
         * read as "never set", which is ENABLED.
         */
        internal val OPTED_OUT_ON_CORRUPTION: ReplaceFileCorruptionHandler<Preferences> =
            ReplaceFileCorruptionHandler { preferencesOf(KEY to false) }

        /** Opt-out: on unless the user said otherwise. */
        private const val DEFAULT_ENABLED = true
    }
}
