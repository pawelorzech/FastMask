package com.fastmask.data.crash

import android.content.Context
import androidx.datastore.core.DataMigration
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import com.fastmask.data.local.settingsDataStore
import kotlinx.coroutines.flow.first

/**
 * The crash reporting opt-out has a DataStore file of its own, owned by
 * [CrashReporting] alone.
 *
 * The shared `settings` store resets a corrupt file to "never set", which for this
 * key means ENABLED. Corruption of *this* file is replaced by an explicit opt-out
 * ([CrashReporting.OPTED_OUT_ON_CORRUPTION]), so collection stays off until the
 * user flips the switch again.
 */
val Context.crashReportingDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "crash_reporting",
    corruptionHandler = CrashReporting.OPTED_OUT_ON_CORRUPTION,
    produceMigrations = { context -> listOf(CrashOptOutMigration(context.settingsDataStore)) },
)

/**
 * One-time move of the opt-out from the old shared `settings` store. When the
 * new store has no value yet and the old one does, the value is copied across
 * and the old key is removed afterwards, so there is a single owner. A user
 * who opted out before the update is still opted out after it.
 *
 * The old key is removed in [cleanUp], which DataStore runs only after the
 * migrated data has been persisted: a crash in between repeats the migration
 * rather than losing the choice.
 */
internal class CrashOptOutMigration(
    private val oldStore: DataStore<Preferences>,
) : DataMigration<Preferences> {

    override suspend fun shouldMigrate(currentData: Preferences): Boolean =
        CrashReporting.KEY !in currentData && oldValue() != null

    override suspend fun migrate(currentData: Preferences): Preferences {
        val value = oldValue() ?: return currentData
        return currentData.toMutablePreferences().apply { this[CrashReporting.KEY] = value }
    }

    override suspend fun cleanUp() {
        oldStore.edit { preferences: MutablePreferences -> preferences.remove(CrashReporting.KEY) }
    }

    /** Only a real Boolean counts: a value of another type says nothing about the choice. */
    private suspend fun oldValue(): Boolean? =
        oldStore.data.first().asMap()[CrashReporting.KEY] as? Boolean
}
