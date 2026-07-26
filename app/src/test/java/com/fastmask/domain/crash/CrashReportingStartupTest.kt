package com.fastmask.domain.crash

import com.fastmask.testutil.FakeCrashReporter
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException

/**
 * The startup path, which runs from `Application.onCreate` and therefore has
 * two obligations the rest of the crash-reporting code does not have.
 *
 * **It must not throw.** An exception escaping a coroutine launched in
 * `onCreate` reaches the thread's default uncaught handler and kills the process
 * before a frame is drawn — for a feature the user is allowed to switch off.
 * Both failure sources are covered here: the preference read, and the SDK call
 * itself (the more likely one, since that is where Firebase initialises).
 *
 * **It must not turn a failed read into a decision.** "No value stored" is a
 * fresh or upgraded install and defaults to on. "Could not read the store" says
 * nothing about what the user chose, and Crashlytics keeps its own persisted
 * flag — so the correct move is to leave the SDK alone rather than reset it to
 * the default, which would silently resume collection for someone who opted out.
 */
class CrashReportingStartupTest {

    private val reporter = FakeCrashReporter()
    private val failures = mutableListOf<Throwable>()

    private fun startup(
        read: suspend () -> CrashReportingPreference,
        isDebugBuild: Boolean = false,
        controller: () -> CrashReportingController = {
            CrashReportingController(reporter = reporter, isDebugBuild = isDebugBuild)
        },
    ) = CrashReportingStartup(
        readPreference = read,
        controller = controller,
        onFailure = { failures += it },
    )

    // --- Missing vs Unreadable, the distinction that matters ----------------

    @Test
    fun `a missing preference applies the opt-out default`() = runTest {
        val result = startup(read = { CrashReportingPreference.Missing }).apply()

        assertEquals(CrashReportingPreference.Missing, result)
        assertEquals(listOf("collection=true"), reporter.calls)
    }

    @Test
    fun `a stored opt-out stops collection and purges queued reports`() = runTest {
        startup(read = { CrashReportingPreference.Stored(enabled = false) }).apply()

        assertEquals(listOf("collection=false", "delete"), reporter.calls)
    }

    /**
     * The purge is not a one-off at the moment of the toggle: it runs on *every*
     * launch while the preference says no. That is what closes the startup
     * window — a crash captured before the stored opt-out had been applied
     * leaves a report on disk, and this is what removes it. It cannot have been
     * uploaded in the meantime, because `setCrashlyticsCollectionEnabled(false)`
     * persists inside the SDK and gates collection from process start.
     */
    @Test
    fun `every launch under an opt-out purges again`() = runTest {
        val startup = startup(read = { CrashReportingPreference.Stored(enabled = false) })

        startup.apply()
        startup.apply()
        startup.apply()

        assertEquals(
            listOf("collection=false", "delete", "collection=false", "delete", "collection=false", "delete"),
            reporter.calls,
        )
    }

    @Test
    fun `a stored opt-in resumes collection`() = runTest {
        startup(read = { CrashReportingPreference.Stored(enabled = true) }).apply()

        assertEquals(listOf("collection=true"), reporter.calls)
    }

    /**
     * The regression this whole type exists for. The user opted out, the
     * preferences file later became unreadable; re-applying the default here
     * would call `setCrashlyticsCollectionEnabled(true)` and overwrite the SDK's
     * own persisted opt-out. Nothing may be said to the SDK at all.
     */
    @Test
    fun `an unreadable preference leaves the sdk untouched`() = runTest {
        val result = startup(read = { CrashReportingPreference.Unreadable }).apply()

        assertEquals(CrashReportingPreference.Unreadable, result)
        assertEquals(emptyList<String>(), reporter.calls)
    }

    @Test
    fun `a read that throws leaves the sdk untouched instead of re-enabling`() = runTest {
        val result = startup(read = { throw IOException("preferences file unreadable") }).apply()

        assertEquals(CrashReportingPreference.Unreadable, result)
        assertEquals(
            "a failed read must never resume collection for someone who opted out",
            emptyList<String>(),
            reporter.calls,
        )
        assertTrue(failures.single() is IOException)
    }

    // --- Never takes down app start -----------------------------------------

    @Test
    fun `a read that throws does not propagate`() = runTest {
        val thrown = runCatching {
            startup(read = { error("DataStore is on fire") }).apply()
        }.exceptionOrNull()

        assertNull("app start must survive a failed preference read", thrown)
    }

    /**
     * The exposure the old code left open: the read was wrapped, the
     * `controller.apply(...)` call right after it was not, and the coroutine
     * scope had no `CoroutineExceptionHandler` to catch what came out.
     */
    @Test
    fun `an sdk that throws does not propagate`() = runTest {
        reporter.failure = IllegalStateException(
            "Default FirebaseApp is not initialized in this process"
        )

        val thrown = runCatching {
            startup(read = { CrashReportingPreference.Missing }).apply()
        }.exceptionOrNull()

        assertNull("app start must survive a crash reporting SDK that cannot start", thrown)
        assertTrue(failures.single() is IllegalStateException)
    }

    /**
     * The SDK is not merely called lazily — it is not even *built* until the
     * preference has been read. Resolving `FirebaseCrashlytics.getInstance()`
     * eagerly used to happen during Hilt member injection, on the main thread,
     * before `super.onCreate()`.
     */
    @Test
    fun `the controller is never built when the preference cannot be read`() = runTest {
        var built = 0

        startup(
            read = { throw IOException("unreadable") },
            controller = {
                built++
                CrashReportingController(reporter = reporter, isDebugBuild = false)
            },
        ).apply()

        assertEquals(0, built)
    }

    @Test
    fun `building the controller throwing does not propagate`() = runTest {
        val thrown = runCatching {
            startup(
                read = { CrashReportingPreference.Missing },
                controller = { error("Default FirebaseApp is not initialized in this process") },
            ).apply()
        }.exceptionOrNull()

        assertNull(thrown)
        assertEquals(emptyList<String>(), reporter.calls)
    }

    /**
     * Swallowing everything must not extend to cancellation: that belongs to
     * the caller's coroutine and has to keep propagating, or a cancelled scope
     * would keep running work.
     */
    @Test
    fun `cancellation still propagates`() = runTest {
        val thrown = runCatching {
            startup(read = { throw CancellationException("scope closed") }).apply()
        }.exceptionOrNull()

        assertTrue("expected the cancellation, got $thrown", thrown is CancellationException)
    }

    // --- Interaction with the debug gate ------------------------------------

    @Test
    fun `a debug build never collects even with the preference missing`() = runTest {
        startup(read = { CrashReportingPreference.Missing }, isDebugBuild = true).apply()

        assertEquals(listOf("collection=false"), reporter.calls)
    }

    // --- The display value is not the SDK value -----------------------------

    @Test
    fun `enabledOrDefault reports on for both missing and unreadable`() {
        assertTrue(CrashReportingPreference.Missing.enabledOrDefault)
        assertTrue(CrashReportingPreference.Unreadable.enabledOrDefault)
        assertTrue(CrashReportingPreference.Stored(enabled = true).enabledOrDefault)
        assertEquals(false, CrashReportingPreference.Stored(enabled = false).enabledOrDefault)
    }

    @Test
    fun `the documented default is opt-out`() {
        assertTrue(
            "opt-out means a fresh install reports until told otherwise",
            CrashReportingPolicy.DEFAULT_ENABLED,
        )
    }
}
