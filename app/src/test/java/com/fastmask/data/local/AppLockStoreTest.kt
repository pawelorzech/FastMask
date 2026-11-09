package com.fastmask.data.local

import androidx.datastore.core.CorruptionException
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.preferencesOf
import androidx.datastore.preferences.core.stringPreferencesKey
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
import java.io.IOException

/**
 * The app lock's own DataStore. File-backed tests build the store the way
 * [appLockDataStore] does (same migration, same corruption handler), so DataStore's own
 * migration and corruption machinery is what is under test.
 */
class AppLockStoreTest {

    @get:Rule
    val temp = TemporaryFolder()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val lockKey = booleanPreferencesKey("app_lock_enabled")

    @After
    fun tearDown() {
        scope.cancel()
    }

    private fun fileStore(oldStore: DataStore<Preferences>): DataStore<Preferences> =
        PreferenceDataStoreFactory.create(
            corruptionHandler = LOCKED_ON_CORRUPTION,
            migrations = listOf(AppLockMigration(oldStore)),
            scope = scope,
            produceFile = { temp.root.resolve("app_lock.preferences_pb") },
        )

    @Test
    fun `a healthy store with no key reads disabled`() = runBlocking {
        assertFalse(AppLockStore(FakePreferencesDataStore()).appLockEnabled.first())
    }

    @Test
    fun `setting the lock reads back, and setting it off disarms it`() = runBlocking {
        val store = AppLockStore(FakePreferencesDataStore())

        store.setAppLockEnabled(true)
        assertTrue(store.appLockEnabled.first())
        assertTrue(store.isAppLockEnabled())

        store.setAppLockEnabled(false)
        assertFalse(store.appLockEnabled.first())
        assertFalse(store.isAppLockEnabled())
    }

    @Test
    fun `a read IOException reads as enabled and does not throw`() = runBlocking {
        val backing = FakePreferencesDataStore().apply { readFailure = IOException("disk") }
        val store = AppLockStore(backing)

        assertTrue(store.appLockEnabled.first())
        assertTrue(store.isAppLockEnabled())
        assertTrue(store.lockAtLaunch())
    }

    @Test
    fun `an undeterminable lock starts the gate locked`() = runBlocking {
        val backing = FakePreferencesDataStore().apply { readFailure = IllegalStateException("boom") }

        assertTrue(AppLockStore(backing).lockAtLaunch())
    }

    @Test
    fun `a never armed healthy store starts the gate unlocked`() = runBlocking {
        assertFalse(AppLockStore(FakePreferencesDataStore()).lockAtLaunch())
    }

    @Test
    fun `the corruption replacement value reads enabled`() = runBlocking {
        val replacement = LOCKED_ON_CORRUPTION.handleCorruption(CorruptionException("corrupt"))

        assertTrue(AppLockStore(FakePreferencesDataStore(replacement)).appLockEnabled.first())
    }

    @Test
    fun `a corrupt file on disk reads enabled and the user can still turn the lock off`() = runBlocking {
        temp.root.resolve("app_lock.preferences_pb").writeBytes(byteArrayOf(0x7f, 0x01, 0x02, 0x03, 0x55))
        val store = AppLockStore(fileStore(FakePreferencesDataStore()))

        assertTrue(store.appLockEnabled.first())

        store.setAppLockEnabled(false)
        assertFalse(store.appLockEnabled.first())
    }

    @Test
    fun `a lock armed before the update stays armed and the old key is gone`() = runBlocking {
        val oldStore = FakePreferencesDataStore(
            preferencesOf(lockKey to true, stringPreferencesKey("accent") to "AMBER"),
        )
        val store = AppLockStore(fileStore(oldStore))

        assertTrue(store.appLockEnabled.first())

        val remaining = oldStore.snapshot().asMap()
        assertFalse("the old store must not keep the key", lockKey in remaining)
        assertEquals("neighbouring settings stay", "AMBER", remaining[stringPreferencesKey("accent")])
    }

    @Test
    fun `an old store without the key migrates nothing and reads disabled`() = runBlocking {
        val oldStore = FakePreferencesDataStore(preferencesOf(stringPreferencesKey("accent") to "AMBER"))
        val store = AppLockStore(fileStore(oldStore))

        assertFalse(store.appLockEnabled.first())
        assertEquals(0, oldStore.writes)
    }

    @Test
    fun `a value already in the new store wins and the old one is left alone`() = runBlocking {
        val oldStore = FakePreferencesDataStore(preferencesOf(lockKey to true))
        val firstRunJob = SupervisorJob()
        val firstRun = CoroutineScope(firstRunJob + Dispatchers.IO)
        AppLockStore(
            PreferenceDataStoreFactory.create(
                scope = firstRun,
                produceFile = { temp.root.resolve("app_lock.preferences_pb") },
            ),
        ).setAppLockEnabled(false)
        // DataStore allows one live instance per file and releases it when its scope completes.
        firstRunJob.cancel()
        firstRunJob.join()

        val store = AppLockStore(fileStore(oldStore))

        assertFalse(store.appLockEnabled.first())
        assertEquals(true, oldStore.snapshot().asMap()[lockKey])
        assertEquals(0, oldStore.writes)
    }
}
