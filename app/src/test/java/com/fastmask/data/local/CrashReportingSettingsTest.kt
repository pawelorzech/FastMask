package com.fastmask.data.local

import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.preferencesOf
import androidx.datastore.preferences.core.stringPreferencesKey
import com.fastmask.domain.crash.CrashReportingPreference
import com.fastmask.testutil.FakePreferencesDataStore
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException

/**
 * The persisted half of the opt-out crash reporting switch.
 *
 * This is a schema change to data that already exists on every installed
 * device, so the interesting cases are not "does a write round-trip" but
 * "what happens to the hundreds of installs that update into a DataStore file
 * which has no such key". Getting that wrong means either silently reporting
 * for someone who would have said no, or silently reporting for nobody.
 *
 * The key name is spelled out as a literal in these tests on purpose: it is
 * part of the on-disk format, so a rename has to break a test rather than
 * quietly orphan every stored value.
 */
class CrashReportingSettingsTest {

    private val crashKey = booleanPreferencesKey("crash_reporting_enabled")

    // The keys SettingsDataStore already owns, as an existing install has them.
    private val languageKey = stringPreferencesKey("language_code")
    private val appModeKey = stringPreferencesKey("app_mode")
    private val accentKey = stringPreferencesKey("accent")
    private val appLockKey = booleanPreferencesKey("app_lock_enabled")
    private val tutorialKey = booleanPreferencesKey("tutorial_completed")
    private val notificationPromptKey = booleanPreferencesKey("notification_prompt_shown")

    private fun existingInstall(): Preferences = preferencesOf(
        languageKey to "pl",
        appModeKey to "DEMO",
        accentKey to "AMBER",
        appLockKey to true,
        tutorialKey to true,
        notificationPromptKey to true,
    )

    private fun settings(
        initial: Preferences = emptyPreferences(),
    ): Pair<CrashReportingSettings, FakePreferencesDataStore> {
        val store = FakePreferencesDataStore(initial)
        return CrashReportingSettings(store) to store
    }

    // --- Default / migration behaviour -------------------------------------

    @Test
    fun `a fresh install with an empty store reports crash reporting as enabled`() = runTest {
        val (settings, _) = settings()

        assertTrue(settings.enabled.first())
    }

    /**
     * The migration case. An install that predates this feature has all the
     * other settings and no crash key; opt-out means those users are opted IN
     * until they say otherwise.
     */
    @Test
    fun `an existing install updating to this version is opted in`() = runTest {
        val (settings, store) = settings(existingInstall())

        assertTrue(settings.enabled.first())
        assertNull(
            "reading must not write a default back into the store",
            store.snapshot()[crashKey],
        )
    }

    @Test
    fun `a stored false means the user opted out`() = runTest {
        val (settings, _) = settings(preferencesOf(crashKey to false))

        assertFalse(settings.enabled.first())
    }

    @Test
    fun `a stored true means the user opted back in`() = runTest {
        val (settings, _) = settings(preferencesOf(crashKey to true))

        assertTrue(settings.enabled.first())
    }

    /**
     * A value of the wrong type under the same name — what a bad migration, a
     * partially written file or a hand-edited store looks like. Reading it as a
     * boolean throws a ClassCastException inside the DataStore mapping, which
     * would take down whatever collects the flow.
     *
     * Chosen degradation: fall back to ENABLED, the documented default. An
     * unreadable value is not an opt-out — the user never said no — and the
     * alternative (silently disabling) would hide crashes from the very build
     * that is corrupting preferences.
     */
    @Test
    fun `a value of the wrong type degrades to the default instead of throwing`() = runTest {
        val corrupt = preferencesOf(stringPreferencesKey("crash_reporting_enabled") to "yes")
        val (settings, _) = settings(corrupt)

        assertTrue(settings.enabled.first())
    }

    /**
     * Real DataStore surfaces an unreadable or corrupted file as an IOException
     * on the data flow. Uncaught, it propagates to the collector and kills the
     * settings screen — over a diagnostics preference.
     */
    @Test
    fun `an unreadable store degrades to the default instead of crashing`() = runTest {
        val (settings, store) = settings()
        store.readFailure = IOException("preferences file unreadable")

        assertTrue(settings.enabled.first())
    }

    // --- Writes -------------------------------------------------------------

    @Test
    fun `opting out persists false under the documented key`() = runTest {
        val (settings, store) = settings()

        settings.setEnabled(false)

        assertEquals(false, store.snapshot()[crashKey])
        assertFalse(settings.enabled.first())
    }

    @Test
    fun `opting back in persists true`() = runTest {
        val (settings, store) = settings(preferencesOf(crashKey to false))

        settings.setEnabled(true)

        assertEquals(true, store.snapshot()[crashKey])
        assertTrue(settings.enabled.first())
    }

    /**
     * Regression guard for the obvious implementation mistake: replacing the
     * whole Preferences object instead of editing one key. That would wipe the
     * user's language, accent, app lock, demo mode and tutorial flag the first
     * time they touched an unrelated switch.
     */
    @Test
    fun `writing the preference leaves every other setting untouched`() = runTest {
        val (settings, store) = settings(existingInstall())

        settings.setEnabled(false)

        val after = store.snapshot()
        assertEquals("pl", after[languageKey])
        assertEquals("DEMO", after[appModeKey])
        assertEquals("AMBER", after[accentKey])
        assertEquals(true, after[appLockKey])
        assertEquals(true, after[tutorialKey])
        assertEquals(true, after[notificationPromptKey])
        assertEquals(false, after[crashKey])
        assertEquals(
            "exactly one key should have been added",
            7,
            after.asMap().size,
        )
    }

    @Test
    fun `setting the same value twice leaves the same state`() = runTest {
        val (settings, store) = settings(existingInstall())

        settings.setEnabled(false)
        val afterFirst = store.snapshot()
        settings.setEnabled(false)
        val afterSecond = store.snapshot()

        assertEquals(afterFirst.asMap(), afterSecond.asMap())
        assertFalse(settings.enabled.first())
    }

    @Test
    fun `toggling off and on again ends up back at enabled`() = runTest {
        val (settings, _) = settings()

        settings.setEnabled(false)
        settings.setEnabled(true)

        assertTrue(settings.enabled.first())
    }

    /**
     * A failed write (full disk, read-only storage) is reported to the caller
     * rather than swallowed here — the settings ViewModel is the layer that
     * decides how to degrade, and it already has a handler for exactly this.
     * Swallowing it here would also hide the failure from every future caller.
     */
    @Test
    fun `a failed write surfaces to the caller`() = runTest {
        val (settings, store) = settings()
        store.writeFailure = IOException("No space left on device")

        val thrown = runCatching { settings.setEnabled(false) }.exceptionOrNull()

        assertTrue("expected an IOException, got $thrown", thrown is IOException)
        assertEquals("the write should have been attempted", 1, store.writes)
    }

    @Test
    fun `a failed write leaves the previously stored value intact`() = runTest {
        val (settings, store) = settings(preferencesOf(crashKey to true))
        store.writeFailure = IOException("No space left on device")

        runCatching { settings.setEnabled(false) }

        assertEquals(true, store.snapshot()[crashKey])
        assertTrue(settings.enabled.first())
    }

    // --- Missing vs Unreadable ---------------------------------------------
    //
    // `enabled` above answers "what should the switch show", and for both of
    // these cases the honest answer is "on". `preference` answers a different
    // question — "did the user actually choose this?" — and the two must not be
    // conflated: startup applies the first and must not touch the SDK on the
    // second, or a transient read failure resurrects collection for someone who
    // deliberately opted out.

    @Test
    fun `no stored key reports Missing, not a stored true`() = runTest {
        val (settings, _) = settings(existingInstall())

        assertEquals(CrashReportingPreference.Missing, settings.preference.first())
    }

    @Test
    fun `a stored value reports Stored in both directions`() = runTest {
        val (optedOut, _) = settings(preferencesOf(crashKey to false))
        val (optedIn, _) = settings(preferencesOf(crashKey to true))

        assertEquals(
            CrashReportingPreference.Stored(enabled = false),
            optedOut.preference.first(),
        )
        assertEquals(
            CrashReportingPreference.Stored(enabled = true),
            optedIn.preference.first(),
        )
    }

    /**
     * The concrete regression: an opted-out user whose preferences file is
     * later truncated or corrupted. The old mapping produced `true` here, and
     * startup handed that to `setCrashlyticsCollectionEnabled(true)` — silently
     * reversing a registered objection. `Unreadable` is what keeps startup from
     * saying anything to the SDK at all.
     */
    @Test
    fun `an unreadable store reports Unreadable, not the default`() = runTest {
        val (settings, store) = settings(preferencesOf(crashKey to false))
        store.readFailure = IOException("preferences file unreadable")

        assertEquals(CrashReportingPreference.Unreadable, settings.preference.first())
    }

    @Test
    fun `a value of the wrong type reports Unreadable rather than an opt-in`() = runTest {
        val corrupt = preferencesOf(stringPreferencesKey("crash_reporting_enabled") to "yes")
        val (settings, _) = settings(corrupt)

        assertEquals(CrashReportingPreference.Unreadable, settings.preference.first())
    }

    /**
     * The two flows stay consistent for display purposes: whatever `preference`
     * says, `enabled` is its display value, and an unknown state displays as on.
     */
    @Test
    fun `the display flag matches the preference for every case`() = runTest {
        val (missing, _) = settings()
        val (optedOut, _) = settings(preferencesOf(crashKey to false))
        val (unreadable, unreadableStore) = settings(preferencesOf(crashKey to false))
        unreadableStore.readFailure = IOException("preferences file unreadable")

        assertTrue(missing.enabled.first())
        assertFalse(optedOut.enabled.first())
        assertTrue(unreadable.enabled.first())
    }

    // --- Synchronous snapshot ----------------------------------------------

    /**
     * `SettingsDataStore.crashReportingEnabledBlocking()` seeds the settings
     * switch from this, so that entering Settings does not `runBlocking` on the
     * main thread for a value the startup pass already read.
     */
    @Test
    fun `the snapshot is empty until something reads the store`() {
        val (settings, _) = settings(preferencesOf(crashKey to false))

        assertNull(settings.lastKnown)
    }

    @Test
    fun `reading the preference fills the snapshot`() = runTest {
        val (settings, _) = settings(preferencesOf(crashKey to false))

        settings.preference.first()

        assertEquals(CrashReportingPreference.Stored(enabled = false), settings.lastKnown)
    }

    @Test
    fun `a write updates the snapshot on the next read`() = runTest {
        val (settings, _) = settings(preferencesOf(crashKey to false))
        settings.preference.first()

        settings.setEnabled(true)
        settings.preference.first()

        assertEquals(CrashReportingPreference.Stored(enabled = true), settings.lastKnown)
    }

    /**
     * "Could not read" is not an answer, so it must not be cached: a transient
     * failure would otherwise keep answering for every later synchronous read
     * in the process, including one taken after the store recovered.
     */
    @Test
    fun `an unreadable store does not overwrite the snapshot`() = runTest {
        val (settings, store) = settings(preferencesOf(crashKey to false))
        settings.preference.first()

        store.readFailure = IOException("preferences file unreadable")
        assertEquals(CrashReportingPreference.Unreadable, settings.preference.first())

        assertEquals(CrashReportingPreference.Stored(enabled = false), settings.lastKnown)
    }
}
