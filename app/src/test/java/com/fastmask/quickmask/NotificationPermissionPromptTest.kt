package com.fastmask.quickmask

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Who gets asked for POST_NOTIFICATIONS, and when.
 *
 * MainActivity evaluated the question once, in `onCreate`, off the START
 * DESTINATION:
 *
 *     maybeRequestNotificationPermission(signedIn = startDestination == EMAIL_LIST)
 *
 * Which means it only ever asked people who were ALREADY signed in when the
 * process started. Someone who opens the app for the first time walks
 * welcome → login → list inside one session; the start destination was
 * WELCOME, the check ran and said no, and nothing re-ran it. On Android 13+
 * they end up with the permission denied and no prompt, ever — so the
 * quick-create confirmation never appears, and with it the "Undo" action, the
 * only way back from a mask created by accident. They get a Toast instead.
 *
 * The fix is not a different predicate but a different NUMBER OF EVALUATIONS:
 * feed session state as it changes, and let something own the "at most once"
 * part. [QuickMaskPolicy.shouldRequestNotificationPermission] stays the
 * predicate (extended, not duplicated); [NotificationPermissionGate] adds the
 * latch that a single `onCreate` call used to stand in for.
 */
class NotificationPermissionPromptTest {

    private val preTiramisu = 32
    private val tiramisu = QuickMaskPolicy.NOTIFICATION_PERMISSION_SDK
    private val current = 36

    private fun shouldAsk(
        sdkInt: Int = current,
        permissionGranted: Boolean = false,
        alreadyAsked: Boolean = false,
        signedIn: Boolean = true,
        locked: Boolean = false,
        demoMode: Boolean = false,
    ) = QuickMaskPolicy.shouldRequestNotificationPermission(
        sdkInt = sdkInt,
        permissionGranted = permissionGranted,
        alreadyAsked = alreadyAsked,
        signedIn = signedIn,
        locked = locked,
        demoMode = demoMode,
    )

    // --- the predicate --------------------------------------------------------

    @Test
    fun `a signed-in user who has not been asked is asked`() {
        assertTrue(shouldAsk())
        assertTrue(shouldAsk(sdkInt = tiramisu))
    }

    @Test
    fun `a signed-out user is not asked`() {
        // Nothing to confirm without a session, and a permission dialog on top
        // of the welcome screen is a dialog about a feature not yet seen.
        assertFalse(shouldAsk(signedIn = false))
    }

    @Test
    fun `below Android 13 there is nothing to ask for`() {
        // Install-time grant; launching the contract would show no dialog and
        // would burn the one-shot flag for nothing.
        assertFalse(shouldAsk(sdkInt = preTiramisu))
        assertFalse(shouldAsk(sdkInt = preTiramisu, signedIn = true, alreadyAsked = false))
    }

    @Test
    fun `nobody is asked twice`() {
        assertFalse(shouldAsk(alreadyAsked = true))
    }

    @Test
    fun `nobody is asked for what they already granted`() {
        assertFalse(shouldAsk(permissionGranted = true))
    }

    /**
     * The biometric gate is up and covering the content. A system permission
     * dialog stacked on the lock screen asks about something the user cannot
     * see, and answering it is the first thing they do in an app they have not
     * unlocked. The prompt waits; the gate re-evaluates after the unlock.
     */
    @Test
    fun `the app lock defers the prompt`() {
        assertFalse(shouldAsk(locked = true))
        // …and the deferral must not consume the one ask: once unlocked, with
        // the persisted flag still false, the same session asks.
        assertTrue(shouldAsk(locked = false))
    }

    /**
     * Demo mode is signed in as far as `AuthRepository.isLoggedIn()` is
     * concerned (it returns true for AppMode.DEMO), but quick-create refuses to
     * run there — `QuickMaskCreator` returns `DemoMode` before touching the
     * account, so the confirmation notification this permission exists for can
     * never be posted. Asking would spend the one prompt Android gives us on a
     * session that cannot use it; worse, a denial in demo is remembered by the
     * platform and makes the later, real request harder to surface.
     *
     * The persisted "already asked" flag stays false through the demo, so the
     * ask happens on the first real sign-in instead.
     */
    @Test
    fun `demo mode is not asked`() {
        assertFalse(shouldAsk(demoMode = true))
        assertFalse(shouldAsk(demoMode = true, signedIn = true, alreadyAsked = false))
    }

    @Test
    fun `leaving demo for a real session still gets the ask`() {
        assertTrue(shouldAsk(demoMode = false, alreadyAsked = false))
    }

    // --- the gate: how many times the predicate is allowed to say yes ---------

    /** One snapshot of the session, as MainActivity would observe it. */
    private data class Session(
        val signedIn: Boolean,
        val locked: Boolean = false,
        val demoMode: Boolean = false,
        val permissionGranted: Boolean = false,
    )

    /**
     * Replays a session as a stream of snapshots through one gate instance and
     * returns the answer for each. [alreadyAsked] is the PERSISTED flag as it
     * stood when the process started.
     */
    private fun replay(
        snapshots: List<Session>,
        alreadyAsked: Boolean = false,
        sdkInt: Int = 36,
    ): List<Boolean> {
        val gate = NotificationPermissionGate(sdkInt)
        return snapshots.map { snapshot ->
            gate.shouldPrompt(
                permissionGranted = snapshot.permissionGranted,
                alreadyAsked = alreadyAsked,
                signedIn = snapshot.signedIn,
                locked = snapshot.locked,
                demoMode = snapshot.demoMode,
            )
        }
    }

    /**
     * The reported bug, as a sequence: the app starts signed out and the user
     * signs in without leaving. The old code answered this question before the
     * sign-in existed and never asked it again.
     */
    @Test
    fun `signing in during the session triggers the prompt`() {
        val answers = replay(
            listOf(
                Session(signedIn = false), // welcome
                Session(signedIn = false), // login screen
                Session(signedIn = true),  // token stored, list shown
            )
        )
        assertEquals(listOf(false, false, true), answers)
    }

    @Test
    fun `the prompt fires exactly once however often the list is re-entered`() {
        // Navigating away and back, a rotation, a resume — every one of these
        // pushes another snapshot. None of them may re-open the dialog, and
        // none of them may wait for the persisted write to land first.
        val answers = replay(
            listOf(
                Session(signedIn = false),
                Session(signedIn = true),
                Session(signedIn = true),
                Session(signedIn = true),
                Session(signedIn = true),
            )
        )
        assertEquals(
            "the gate asked ${answers.count { it }} time(s): $answers",
            1,
            answers.count { it },
        )
        assertTrue("the ask must land on the sign-in, not later", answers[1])
    }

    @Test
    fun `a restart after the prompt does not ask again`() {
        // Second process: the persisted flag is true from the first run.
        val answers = replay(
            listOf(Session(signedIn = true), Session(signedIn = true)),
            alreadyAsked = true,
        )
        assertEquals(listOf(false, false), answers)
    }

    @Test
    fun `an unlock during the session opens the deferred prompt`() {
        // Cold start straight onto the list, behind the app lock: the first
        // snapshots are gated, the one after the unlock asks.
        val answers = replay(
            listOf(
                Session(signedIn = true, locked = true),
                Session(signedIn = true, locked = true),
                Session(signedIn = true, locked = false),
                Session(signedIn = true, locked = false),
            )
        )
        assertEquals(listOf(false, false, true, false), answers)
    }

    @Test
    fun `a demo session that becomes a real one asks once, after the switch`() {
        val answers = replay(
            listOf(
                Session(signedIn = true, demoMode = true),
                Session(signedIn = true, demoMode = true),
                Session(signedIn = false), // demo exited, back to welcome
                Session(signedIn = true, demoMode = false),
                Session(signedIn = true, demoMode = false),
            )
        )
        assertEquals(listOf(false, false, false, true, false), answers)
    }

    @Test
    fun `a session below Android 13 never asks`() {
        val answers = replay(
            listOf(Session(signedIn = false), Session(signedIn = true), Session(signedIn = true)),
            sdkInt = 32,
        )
        assertTrue("Android 12 was prompted: $answers", answers.none { it })
    }

    @Test
    fun `a session that already holds the permission never asks`() {
        val answers = replay(
            listOf(
                Session(signedIn = false, permissionGranted = true),
                Session(signedIn = true, permissionGranted = true),
            )
        )
        assertTrue("already granted, still prompted: $answers", answers.none { it })
    }

    @Test
    fun `the gate agrees with the policy on the first snapshot`() {
        // The latch is the ONLY thing the gate adds; the rules stay in
        // QuickMaskPolicy so there is one place to get them wrong.
        val cases = listOf(
            Session(signedIn = true),
            Session(signedIn = false),
            Session(signedIn = true, locked = true),
            Session(signedIn = true, demoMode = true),
            Session(signedIn = true, permissionGranted = true),
        )
        cases.forEach { snapshot ->
            val gateSaid = replay(listOf(snapshot)).single()
            val policySaid = shouldAsk(
                permissionGranted = snapshot.permissionGranted,
                signedIn = snapshot.signedIn,
                locked = snapshot.locked,
                demoMode = snapshot.demoMode,
            )
            assertEquals("gate and policy disagree about $snapshot", policySaid, gateSaid)
        }
    }
}
