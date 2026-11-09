package com.fastmask.data.crash

import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.preferencesOf
import androidx.datastore.preferences.core.stringPreferencesKey
import com.fastmask.testutil.FakeCrashReporter
import com.fastmask.testutil.FakePreferencesDataStore
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException

/**
 * The crash reporting opt-out end to end, against a fake [com.fastmask.domain.crash.CrashReporter]
 * and an in-memory DataStore whose reads and writes can be made to fail.
 *
 * Three obligations drive these tests:
 *  - **Opt-out is sticky.** The key name is a contract with installed copies, no
 *    key means ENABLED, and a store that cannot be read must never be turned
 *    into "enabled" for someone who opted out.
 *  - **App start cannot fail.** `applyAtStartup` runs from `Application.onCreate`;
 *    neither the read nor the SDK may take the process down.
 *  - **Opt-out is real.** It stops collection and drops unsent reports, in that
 *    order, even if persisting the choice fails afterwards.
 */
class CrashReportingTest {

    private val crashKey = booleanPreferencesKey("crash_reporting_enabled")
    private val reporter = FakeCrashReporter()
    private val failures = mutableListOf<Throwable>()

    private fun crashReporting(
        store: FakePreferencesDataStore = FakePreferencesDataStore(),
        isDebugBuild: Boolean = false,
    ) = CrashReporting(
        dataStore = store,
        reporter = reporter,
        isDebugBuild = isDebugBuild,
        onFailure = { failures += it },
    )

    private fun storeWith(enabled: Boolean) =
        FakePreferencesDataStore(preferencesOf(crashKey to enabled))

    // --- The stored preference ----------------------------------------------

    // crashKey above spells the key literally on purpose: it is a contract with installed copies.
    @Test
    fun `no stored key reads as enabled`() = runTest {
        assertTrue(crashReporting().enabled.first())
    }

    @Test
    fun `a stored value is read in both directions`() = runTest {
        assertFalse(crashReporting(storeWith(false)).enabled.first())
        assertTrue(crashReporting(storeWith(true)).enabled.first())
    }

    @Test
    fun `an unreadable store shows the default instead of crashing the switch`() = runTest {
        val store = FakePreferencesDataStore().apply { readFailure = IOException("corrupt") }

        assertTrue(crashReporting(store).enabled.first())
    }

    @Test
    fun `a value of the wrong type shows the default instead of throwing`() = runTest {
        val store = FakePreferencesDataStore(preferencesOf(stringPreferencesKey("crash_reporting_enabled") to "nope"))

        assertTrue(crashReporting(store).enabled.first())
    }

    @Test
    fun `a failure that is not an io error is not swallowed by the display flow`() = runTest {
        val store = FakePreferencesDataStore().apply { readFailure = IllegalStateException("bug") }

        val thrown = runCatching { crashReporting(store).enabled.first() }.exceptionOrNull()

        assertTrue(thrown is IllegalStateException)
    }

    // --- Synchronous seed for the switch ------------------------------------

    @Test
    fun `the seed shows a stored opt-out before anything has been read`() {
        assertFalse(crashReporting(storeWith(false)).enabledBlocking())
    }

    @Test
    fun `the seed defaults to enabled for an untouched install`() {
        assertTrue(crashReporting().enabledBlocking())
    }

    @Test
    fun `the seed defaults to enabled when the store cannot be read`() {
        val store = FakePreferencesDataStore().apply { readFailure = IOException("corrupt") }

        assertTrue(crashReporting(store).enabledBlocking())
    }

    @Test
    fun `the seed follows a write without another read`() = runTest {
        val store = FakePreferencesDataStore()
        val crash = crashReporting(store)

        crash.setEnabled(false)
        store.readFailure = IOException("gone since")

        assertFalse("the last definite answer wins over a later failed read", crash.enabledBlocking())
    }

    // --- Startup: Missing vs Unreadable, the distinction that matters -------

    @Test
    fun `startup with no stored key applies the opt-out default`() = runTest {
        crashReporting().applyAtStartup()

        assertEquals(listOf("collection=true"), reporter.calls)
    }

    @Test
    fun `startup with a stored opt-out stops collection and purges queued reports`() = runTest {
        crashReporting(storeWith(false)).applyAtStartup()

        assertEquals(listOf("collection=false", "delete"), reporter.calls)
    }

    /**
     * The purge is not a one-off at the moment of the toggle: it runs on every
     * launch while the preference says no, which is what removes a report left
     * by a crash that landed before the opt-out had been applied.
     */
    @Test
    fun `every launch under an opt-out purges again`() = runTest {
        val crash = crashReporting(storeWith(false))

        crash.applyAtStartup()
        crash.applyAtStartup()

        assertEquals(
            listOf("collection=false", "delete", "collection=false", "delete"),
            reporter.calls,
        )
    }

    @Test
    fun `startup with a stored opt-in resumes collection`() = runTest {
        crashReporting(storeWith(true)).applyAtStartup()

        assertEquals(listOf("collection=true"), reporter.calls)
    }

    /**
     * The regression this design exists for. The user opted out, the file later
     * became unreadable; re-applying the default would overwrite the SDK's own
     * persisted opt-out. Nothing may be said to the SDK at all.
     */
    @Test
    fun `an unreadable store leaves the sdk untouched at startup`() = runTest {
        val store = storeWith(false).apply { readFailure = IOException("preferences file unreadable") }

        crashReporting(store).applyAtStartup()

        assertEquals(
            "a failed read must never resume collection for someone who opted out",
            emptyList<String>(),
            reporter.calls,
        )
    }

    @Test
    fun `a value of the wrong type leaves the sdk untouched at startup`() = runTest {
        val store = FakePreferencesDataStore(preferencesOf(stringPreferencesKey("crash_reporting_enabled") to "x"))

        crashReporting(store).applyAtStartup()

        assertEquals(emptyList<String>(), reporter.calls)
    }

    @Test
    fun `a read that throws something unexpected leaves the sdk untouched and is reported`() = runTest {
        val store = FakePreferencesDataStore().apply { readFailure = IllegalStateException("on fire") }

        val thrown = runCatching { crashReporting(store).applyAtStartup() }.exceptionOrNull()

        assertNull("app start must survive a failed preference read", thrown)
        assertEquals(emptyList<String>(), reporter.calls)
        assertTrue(failures.single() is IllegalStateException)
    }

    // --- Startup never takes down the app ------------------------------------

    @Test
    fun `an sdk that throws does not propagate from startup`() = runTest {
        reporter.failure = IllegalStateException("Default FirebaseApp is not initialized in this process")

        val thrown = runCatching { crashReporting().applyAtStartup() }.exceptionOrNull()

        assertNull("app start must survive a crash reporting SDK that cannot start", thrown)
        assertTrue(failures.single() is IllegalStateException)
    }

    /**
     * `FirebaseCrashlytics.getInstance()` throws when no `FirebaseApp` exists
     * (OEM ROMs without the init provider, app cloners, direct boot). The
     * reporter is injected on the main thread before `super.onCreate()`, so
     * building it must not reach the SDK; only a call may. A unit test has no
     * `FirebaseApp`, which makes the second half the control for the first.
     */
    @Test
    fun `the firebase reporter reaches the sdk on a call, not on construction`() {
        val firebase = FirebaseCrashlyticsReporter()

        assertTrue(runCatching { firebase.setCollectionEnabled(false) }.isFailure)
    }

    /** Swallowing everything must not extend to cancellation. */
    @Test
    fun `cancellation still propagates from startup`() = runTest {
        val store = FakePreferencesDataStore().apply { readFailure = CancellationException("scope closed") }

        val thrown = runCatching { crashReporting(store).applyAtStartup() }.exceptionOrNull()

        assertTrue("expected the cancellation, got $thrown", thrown is CancellationException)
    }

    // --- Policy: debug builds never collect ----------------------------------

    @Test
    fun `a debug build disables collection at startup even with reporting left on`() = runTest {
        crashReporting(isDebugBuild = true).applyAtStartup()

        assertEquals(listOf("collection=false"), reporter.calls)
    }

    @Test
    fun `a debug build still honours an explicit opt-out by purging reports`() = runTest {
        crashReporting(storeWith(false), isDebugBuild = true).applyAtStartup()

        assertEquals(listOf("collection=false", "delete"), reporter.calls)
    }

    @Test
    fun `a debug build never enables collection through the switch either`() = runTest {
        crashReporting(isDebugBuild = true).setEnabled(true)

        assertEquals(listOf("collection=false"), reporter.calls)
    }

    // --- The switch -----------------------------------------------------------

    @Test
    fun `opting out stops collection first, then drops unsent reports, then persists`() = runTest {
        val store = FakePreferencesDataStore()

        crashReporting(store).setEnabled(false)

        assertEquals(listOf("collection=false", "delete"), reporter.calls)
        assertEquals(false, store.snapshot()[crashKey])
    }

    @Test
    fun `opting back in resumes collection without purging`() = runTest {
        val store = storeWith(false)
        val crash = crashReporting(store)

        crash.setEnabled(true)

        assertEquals(listOf("collection=true"), reporter.calls)
        assertEquals(true, store.snapshot()[crashKey])
        assertTrue(crash.enabled.first())
    }

    @Test
    fun `writing the preference leaves every other setting untouched`() = runTest {
        val language = stringPreferencesKey("language_code")
        val accent = stringPreferencesKey("accent")
        val tutorial = booleanPreferencesKey("tutorial_completed")
        val store = FakePreferencesDataStore(
            preferencesOf(language to "pl", accent to "AMBER", tutorial to true)
        )

        crashReporting(store).setEnabled(false)

        val after = store.snapshot()
        assertEquals("pl", after[language])
        assertEquals("AMBER", after[accent])
        assertEquals(true, after[tutorial])
    }

    @Test
    fun `setting the same value twice leaves the same state`() = runTest {
        val store = FakePreferencesDataStore()
        val crash = crashReporting(store)

        crash.setEnabled(false)
        crash.setEnabled(false)

        assertEquals(false, store.snapshot()[crashKey])
        assertFalse(crash.enabled.first())
    }

    /**
     * A full disk must neither turn an opt-out into a crash nor into a no-op:
     * collection stops for this session, the failure surfaces to the caller, and
     * the previously stored value stays intact.
     */
    @Test
    fun `a failed write still stops collection, surfaces, and keeps the stored value`() = runTest {
        val store = storeWith(true).apply { writeFailure = IOException("No space left on device") }
        val crash = crashReporting(store)

        val thrown = runCatching { crash.setEnabled(false) }.exceptionOrNull()

        assertTrue(thrown is IOException)
        assertEquals(listOf("collection=false", "delete"), reporter.calls)
        assertEquals(true, store.snapshot()[crashKey])
        assertTrue("the seed must not claim an opt-out that was never stored", crash.enabledBlocking())
    }

    /**
     * Firebase initialises on the first call, straight off a tap, and throws
     * where the default FirebaseApp never came up. The choice must still be
     * persisted so the next launch re-applies it.
     */
    @Test
    fun `an sdk that cannot start does not stop the choice from being persisted`() = runTest {
        reporter.failure = IllegalStateException("Default FirebaseApp is not initialized in this process")
        val store = FakePreferencesDataStore()

        crashReporting(store).setEnabled(false)

        assertEquals(false, store.snapshot()[crashKey])
        assertTrue(failures.single() is IllegalStateException)
    }
}
