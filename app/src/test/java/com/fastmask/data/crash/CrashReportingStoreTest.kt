package com.fastmask.data.crash

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.preferencesOf
import androidx.datastore.preferences.core.stringPreferencesKey
import com.fastmask.testutil.FakeCrashReporter
import com.fastmask.testutil.FakePreferencesDataStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/**
 * The crash reporting opt-out's own DataStore, built exactly the way
 * [crashReportingDataStore] builds it (same migration, same corruption
 * handler) over a real file, so DataStore's own migration and corruption
 * machinery is what is under test.
 */
class CrashReportingStoreTest {

    @get:Rule
    val temp = TemporaryFolder()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val crashKey = booleanPreferencesKey("crash_reporting_enabled")
    private val reporter = FakeCrashReporter()

    @After
    fun tearDown() {
        scope.cancel()
    }

    private fun newStore(oldStore: DataStore<Preferences>): DataStore<Preferences> =
        PreferenceDataStoreFactory.create(
            corruptionHandler = CrashReporting.OPTED_OUT_ON_CORRUPTION,
            migrations = listOf(CrashOptOutMigration(oldStore)),
            scope = scope,
            produceFile = { temp.root.resolve("crash_reporting.preferences_pb") },
        )

    private fun crashReporting(store: DataStore<Preferences>) = CrashReporting(
        dataStore = store,
        reporter = { reporter },
        isDebugBuild = false,
    )

    @Test
    fun `an opt-out made before the update is still an opt-out after it, and the old key is gone`() = runBlocking {
        val oldStore = FakePreferencesDataStore(
            preferencesOf(crashKey to false, stringPreferencesKey("accent") to "AMBER"),
        )
        val crash = crashReporting(newStore(oldStore))

        assertFalse(crash.enabled.first())
        crash.applyAtStartup()
        assertEquals(listOf("collection=false", "delete"), reporter.calls)

        val remaining = oldStore.snapshot().asMap()
        assertFalse("the old store must not keep the key", crashKey in remaining)
        assertEquals("neighbouring settings stay", "AMBER", remaining[stringPreferencesKey("accent")])
    }

    @Test
    fun `an explicit opt-in made before the update is carried over too`() = runBlocking {
        val oldStore = FakePreferencesDataStore(preferencesOf(crashKey to true))
        val store = newStore(oldStore)

        assertTrue(crashReporting(store).enabled.first())
        assertEquals(true, store.data.first().asMap()[crashKey])
        assertFalse(crashKey in oldStore.snapshot().asMap())
    }

    @Test
    fun `never set anywhere stays at the default and writes nothing`() = runBlocking {
        val oldStore = FakePreferencesDataStore(preferencesOf(stringPreferencesKey("accent") to "AMBER"))
        val store = newStore(oldStore)

        assertTrue(crashReporting(store).enabled.first())
        assertFalse(crashKey in store.data.first().asMap())
        assertFalse(crashKey in oldStore.snapshot().asMap())
        assertEquals(0, oldStore.writes)

        crashReporting(store).applyAtStartup()
        assertEquals(listOf("collection=true"), reporter.calls)
    }

    @Test
    fun `a value in the new store wins and the old one is left alone`() = runBlocking {
        val oldStore = FakePreferencesDataStore(preferencesOf(crashKey to false))
        // The new store already holds an answer, written on its own.
        val firstRun = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        PreferenceDataStoreFactory.create(
            scope = firstRun,
            produceFile = { temp.root.resolve("crash_reporting.preferences_pb") },
        ).let { crashReporting(it).setEnabled(true) }
        firstRun.cancel()

        // Restart: the same file, now with the migration attached.
        val store = newStore(oldStore)

        assertTrue(crashReporting(store).enabled.first())
        assertEquals(false, oldStore.snapshot().asMap()[crashKey])
        assertEquals(0, oldStore.writes)
    }

    @Test
    fun `a corrupt crash reporting file never reads as enabled and leaves collection off`() = runBlocking {
        temp.root.resolve("crash_reporting.preferences_pb").writeBytes(byteArrayOf(0x7f, 0x01, 0x02, 0x03, 0x55))
        val store = newStore(FakePreferencesDataStore())
        val crash = crashReporting(store)

        assertFalse(crash.enabled.first())
        crash.applyAtStartup()
        assertEquals(listOf("collection=false", "delete"), reporter.calls)
        assertEquals(false, store.data.first().asMap()[crashKey])
    }

    @Test
    fun `after corruption the user can still flip the switch back on`() = runBlocking {
        temp.root.resolve("crash_reporting.preferences_pb").writeBytes(byteArrayOf(0x7f, 0x01, 0x02, 0x03, 0x55))
        val crash = crashReporting(newStore(FakePreferencesDataStore()))

        crash.setEnabled(true)

        assertTrue(crash.enabled.first())
    }
}
