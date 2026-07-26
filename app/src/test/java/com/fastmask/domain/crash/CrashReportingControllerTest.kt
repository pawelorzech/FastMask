package com.fastmask.domain.crash

import com.fastmask.testutil.FakeCrashReporter
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Side effects of applying the preference.
 *
 * Turning reporting off is not just "stop collecting from now on": reports
 * captured before the user opted out are still queued on the device and would
 * be uploaded on the next launch. Opting out has to purge them, otherwise the
 * switch is a lie.
 */
class CrashReportingControllerTest {

    private val reporter = FakeCrashReporter()

    private fun controller(isDebugBuild: Boolean) =
        CrashReportingController(reporter = reporter, isDebugBuild = isDebugBuild)

    @Test
    fun `opting out stops collection and drops everything not yet uploaded`() {
        controller(isDebugBuild = false).apply(userEnabled = false)

        assertEquals(listOf("collection=false", "delete"), reporter.calls)
    }

    @Test
    fun `leaving reporting on enables collection and keeps queued reports`() {
        controller(isDebugBuild = false).apply(userEnabled = true)

        assertEquals(listOf("collection=true"), reporter.calls)
    }

    // Development crashes must never reach the production console, whatever the
    // preference says. Queued reports stay put: the user did not ask for them
    // to be deleted, and a debug build has nothing to purge on their behalf.
    @Test
    fun `a debug build disables collection even with reporting left on`() {
        controller(isDebugBuild = true).apply(userEnabled = true)

        assertEquals(listOf("collection=false"), reporter.calls)
    }

    @Test
    fun `a debug build still honours an explicit opt-out by purging reports`() {
        controller(isDebugBuild = true).apply(userEnabled = false)

        assertEquals(listOf("collection=false", "delete"), reporter.calls)
    }

    // Applying the same value twice happens on every app start, and again when
    // the settings screen re-emits the stored value after a rotation.
    @Test
    fun `applying the same value twice is safe`() {
        val controller = controller(isDebugBuild = false)

        controller.apply(userEnabled = true)
        controller.apply(userEnabled = true)

        assertEquals(listOf("collection=true", "collection=true"), reporter.calls)
    }

    @Test
    fun `re-enabling after an opt-out turns collection back on without deleting again`() {
        val controller = controller(isDebugBuild = false)

        controller.apply(userEnabled = false)
        controller.apply(userEnabled = true)

        assertEquals(listOf("collection=false", "delete", "collection=true"), reporter.calls)
    }
}
