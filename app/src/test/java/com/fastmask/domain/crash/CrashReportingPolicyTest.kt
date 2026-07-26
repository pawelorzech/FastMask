package com.fastmask.domain.crash

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The whole "may we collect this crash?" decision, with no Firebase in sight.
 *
 * Two independent rules meet here and they are easy to get backwards:
 *  - the build type is a hard gate (development crashes must never pollute the
 *    production console — a single noisy dev loop can bury the real reports),
 *  - the user preference is opt-out, so "not stored" already reads as `true`
 *    by the time it reaches this function.
 */
class CrashReportingPolicyTest {

    @Test
    fun `a debug build never collects even when the user left reporting on`() {
        assertFalse(CrashReportingPolicy.shouldCollect(isDebugBuild = true, userEnabled = true))
    }

    @Test
    fun `a debug build never collects when the user opted out`() {
        assertFalse(CrashReportingPolicy.shouldCollect(isDebugBuild = true, userEnabled = false))
    }

    @Test
    fun `a release build collects when the user left reporting on`() {
        assertTrue(CrashReportingPolicy.shouldCollect(isDebugBuild = false, userEnabled = true))
    }

    @Test
    fun `a release build does not collect when the user opted out`() {
        assertFalse(CrashReportingPolicy.shouldCollect(isDebugBuild = false, userEnabled = false))
    }

    /** The full truth table in one place, so a future edit cannot flip a corner. */
    @Test
    fun `the decision table is exhaustive`() {
        val expected = mapOf(
            (true to true) to false,
            (true to false) to false,
            (false to true) to true,
            (false to false) to false,
        )

        val actual = expected.keys.associateWith { (debug, userEnabled) ->
            CrashReportingPolicy.shouldCollect(isDebugBuild = debug, userEnabled = userEnabled)
        }

        assertEquals(expected, actual)
    }
}
