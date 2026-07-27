package com.fastmask.domain.share

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * The half of the share bug that a pure function cannot express.
 *
 * [ShareRouter] can be perfect and the feature still dead, because the defect
 * was never in the decision itself — it was in the INPUT. `MainActivity` fed
 * the routing condition `startDestination`, a value computed once inside the
 * `onCreate` coroutine and then frozen for the life of the process:
 *
 * ```kotlin
 * if (startDestination == NavRoutes.EMAIL_LIST) { navController.navigate(…) }
 * shareConsumed = true
 * pendingShare.value = null
 * ```
 *
 * A process launched signed out keeps `WELCOME` there forever, so every share
 * arriving after an in-session sign-in fell straight through to the two
 * clearing lines — no navigation, no message. Verified on emulator-5554: the
 * intent reached `onNewIntent` on the same ActivityRecord in both the failing
 * and the working run; only the launch-time destination differed.
 *
 * These tests read the source, in the style already established by
 * `QuickMaskChannelTest`, because the alternative — an instrumented test that
 * launches signed out, signs in and then delivers a share — is not something
 * this module's unit suite can run.
 */
class ShareRoutingWiringTest {

    private val mainActivitySource =
        File("src/main/java/com/fastmask/MainActivity.kt").readText()

    @Test
    fun `share routing is not keyed on the launch-time destination`() {
        // The exact expression that was reproduced on the emulator.
        val readsStartDestination = Regex("""startDestination\s*==""").containsMatchIn(mainActivitySource)

        assertFalse(
            "MainActivity still compares startDestination — the value is decided once in " +
                "onCreate and never updated, so a user who signs in without leaving the app " +
                "silently loses the share target for the rest of the process. Route on the " +
                "live session state instead (ShareRouter.route(signedIn = …)).",
            readsStartDestination,
        )
    }

    @Test
    fun `MainActivity routes shares through ShareRouter`() {
        // One decision table, in one testable place. A second copy of the rules
        // inlined in the Activity is how the first one drifted.
        assertTrue(
            "MainActivity does not use ShareRouter; the share decision must live in the " +
                "unit-testable router, not in a condition inside setContent",
            mainActivitySource.contains("ShareRouter"),
        )
    }

    @Test
    fun `the pending share is a distinct value per delivery`() {
        // The Compose state holder must not be able to swallow a repeat of the
        // same link. ShareInbox mints the ids; MainActivity has to use it.
        assertTrue(
            "MainActivity must obtain pending shares from ShareInbox, so two deliveries of " +
                "the same link are never structurally equal",
            mainActivitySource.contains("ShareInbox"),
        )
    }

    @Test
    fun `a share that cannot be acted on is not dropped in silence`() {
        // The user-visible half of the bug: the app came to the front unchanged
        // and said nothing, so the reporter assumed a mis-tap and retried.
        assertTrue(
            "MainActivity handles ShareRoute.RejectSignedOut but shows nothing for it — a " +
                "rejected share must reach the user (snackbar/message), never silence",
            !mainActivitySource.contains("RejectSignedOut") ||
                Regex("""RejectSignedOut[\s\S]{0,600}?(snackbar|Snackbar|showMessage|Toast|R\.string)""")
                    .containsMatchIn(mainActivitySource),
        )
    }

    @Test
    fun `the biometric gate still holds shares back`() {
        // Non-negotiable: the share waits behind the lock. Whatever the routing
        // looks like after the fix, the reason it is structured that way has to
        // stay written down where the next person will read it.
        val mentionsTheGate = Regex(
            """(?i)(share|udost)[\s\S]{0,200}?(lock|gate|biometric)|""" +
                """(?i)(lock|gate|biometric)[\s\S]{0,200}?share"""
        ).containsMatchIn(mainActivitySource)

        assertTrue(
            "MainActivity no longer explains that a pending share waits behind the " +
                "biometric gate. That is a deliberate privacy mechanism, not an accident of " +
                "where the code sits — say so, or the next refactor lifts the share out of " +
                "the gated branch.",
            mentionsTheGate,
        )
        assertTrue(
            "the share routing must stay inside the unlocked branch",
            mainActivitySource.contains("WaitForUnlock") || mainActivitySource.contains("isLocked"),
        )
    }

    @Test
    fun `onNewIntent still delivers shares`() {
        // The OEM share sheet on the reporter's device delivers into the running
        // top-most instance; without this override the feature has no path at
        // all outside a cold start.
        assertTrue(
            "MainActivity must keep overriding onNewIntent — a share is delivered there " +
                "whenever the app is already running",
            Regex("""override fun onNewIntent""").containsMatchIn(mainActivitySource),
        )
        assertTrue(
            "onNewIntent must call setIntent, or a later configuration change re-reads the " +
                "stale launch intent",
            mainActivitySource.contains("setIntent(intent)"),
        )
    }
}
