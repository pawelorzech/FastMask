package com.fastmask.testutil

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import com.fastmask.domain.crash.CrashReporter
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow

/**
 * Records what the app asked the crash backend to do, in order.
 *
 * Order matters: collection has to be switched off *before* pending reports are
 * dropped, otherwise a crash landing in between is written back after the purge
 * and the user's opt-out leaks a report anyway.
 */
class FakeCrashReporter : CrashReporter {

    val calls = mutableListOf<String>()

    /**
     * Thrown by every call, the way the real SDK fails when the default
     * `FirebaseApp` was never initialised for this process. Nothing above this
     * seam may let that reach app start.
     */
    var failure: Throwable? = null

    override fun setCollectionEnabled(enabled: Boolean) {
        calls += "collection=$enabled"
        failure?.let { throw it }
    }

    override fun deleteUnsentReports() {
        calls += "delete"
        failure?.let { throw it }
    }
}

/**
 * In-memory `DataStore<Preferences>`, so the preference layer can be tested on
 * the JVM without Robolectric — and, more importantly, so read and write
 * failures can be injected. A user with a full disk or a corrupted preferences
 * file must not get a crash out of a *crash reporting* setting.
 */
class FakePreferencesDataStore(
    initial: Preferences = emptyPreferences(),
) : DataStore<Preferences> {

    private val state = MutableStateFlow(initial)

    /** Thrown by the data flow, the way a corrupted store surfaces. */
    var readFailure: Throwable? = null

    /** Thrown by [updateData], the way a failed write surfaces. */
    var writeFailure: Throwable? = null

    /** How many write attempts reached the store. */
    var writes = 0
        private set

    override val data: Flow<Preferences> = flow {
        readFailure?.let { throw it }
        emitAll(state)
    }

    override suspend fun updateData(
        transform: suspend (t: Preferences) -> Preferences,
    ): Preferences {
        writes++
        writeFailure?.let { throw it }
        val updated = transform(state.value)
        state.value = updated
        return updated
    }

    /** Current contents, for assertions about neighbouring keys. */
    fun snapshot(): Preferences = state.value
}
