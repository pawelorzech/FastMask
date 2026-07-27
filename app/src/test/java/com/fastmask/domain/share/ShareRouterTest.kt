package com.fastmask.domain.share

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Share routing — the decision that made the share target dead outside a cold
 * start.
 *
 * ## What was actually observed (emulator-5554, Pixel 9a / Android 16, debug
 * build of this worktree)
 *
 * Two runs, identical intent, identical delivery mode. `am start … -a SEND
 * --activity-single-top` answered *"Warning: Activity not started, intent has
 * been delivered to currently running top-most instance"* in BOTH, and the
 * ActivityRecord id was unchanged in both — so both went through
 * `onNewIntent`, not through a fresh `onCreate`.
 *
 * - **Run A** — process launched SIGNED OUT (welcome), demo entered without
 *   leaving, sitting on the mask list. Share arrives → the app stays on the
 *   list (`Masked`, `8 ACTIVE · 10 TOTAL`). A second share, with a DIFFERENT
 *   link, does the same. No create screen, no message.
 * - **Run B** — same install, same persisted state, but the process launched
 *   already signed in. Share arrives → `New mask` / `Configure a new masked
 *   address`, domain prefilled.
 *
 * The single difference between the runs is the value `startDestination` was
 * given once in `onCreate`. That is the whole bug, and it is why nothing in
 * this file knows what a start destination is: [ShareRouter.route] is fed the
 * LIVE session state.
 *
 * Sharing from the Settings screen routed correctly in run B, and the same link
 * shared twice in a row routed twice — so "the wrong screen" and "Compose
 * structural equality" were NOT what the user hit. They are covered here
 * anyway: both are one careless refactor away from becoming true, and the
 * second one is why [ShareRequest] carries a delivery id at all.
 */
class ShareRouterTest {

    private val github = SharePrefill(
        forDomain = "github.com",
        url = "https://www.github.com/signup",
        description = "github.com",
    )

    private fun request(prefill: SharePrefill? = github, deliveryId: Long = 1) =
        ShareRequest(prefill = prefill, deliveryId = deliveryId)

    // --- the reproduced failure ----------------------------------------------

    @Test
    fun `a share reaching a running app on the mask list opens the create screen`() {
        // Run A above: this returned nothing at all, because the app had been
        // launched signed out and the launch-time destination was still WELCOME.
        val route = ShareRouter.route(request(), signedIn = true, locked = false)

        assertEquals(ShareRoute.OpenCreate(github), route)
    }

    @Test
    fun `signing in during the session does not disarm the share target`() {
        // The exact reproduction: welcome -> demo/sign-in -> list, all in one
        // process. There is no input here to express "how the process started",
        // and that absence IS the contract — see ShareRoutingWiringTest for the
        // guard that MainActivity cannot reintroduce one.
        val route = ShareRouter.route(request(), signedIn = true, locked = false)

        assertTrue(
            "a session that became valid mid-life must route like any other: $route",
            route is ShareRoute.OpenCreate,
        )
    }

    // --- the screen the app happens to be on ---------------------------------

    @Test
    fun `the screen the app is standing on is irrelevant to the decision`() {
        // Mask detail, settings, an already-open create form: the share is a
        // navigation command, not a function of where the user was. Encoded as
        // "the route has no screen parameter" — the decision cannot depend on
        // something it is never told.
        val fromAnywhere = ShareRouter.route(request(), signedIn = true, locked = false)

        assertEquals(ShareRoute.OpenCreate(github), fromAnywhere)
    }

    // --- two shares in a row -------------------------------------------------

    @Test
    fun `the same link shared twice produces two distinct requests`() {
        // The trap: a payload-only data class is `equals` to its predecessor, so
        // a Compose `mutableStateOf` holding it reports "no change" and the
        // effect that navigates never re-runs.
        val inbox = ShareInbox()

        val first = inbox.offer(github)
        val second = inbox.offer(github)

        assertNotEquals(
            "two deliveries of the same link are structurally equal — a state " +
                "holder using structuralEqualityPolicy() would swallow the second",
            first,
            second,
        )
        assertTrue("delivery ids must increase: $first then $second", second.deliveryId > first.deliveryId)
    }

    @Test
    fun `both deliveries of the same link route to the create screen`() {
        val inbox = ShareInbox()

        val first = ShareRouter.route(inbox.offer(github), signedIn = true, locked = false)
        val second = ShareRouter.route(inbox.offer(github), signedIn = true, locked = false)

        assertEquals(ShareRoute.OpenCreate(github), first)
        assertEquals(ShareRoute.OpenCreate(github), second)
    }

    @Test
    fun `delivery ids are never reused`() {
        val inbox = ShareInbox()
        val ids = (1..50).map { inbox.offer(github).deliveryId }

        assertEquals("duplicate delivery ids: $ids", ids.size, ids.toSet().size)
    }

    @Test
    fun `an empty share still gets its own delivery id`() {
        // Text with no link is a real share too — two of them in a row must not
        // collapse into one for the same equality reason.
        val inbox = ShareInbox()

        assertNotEquals(inbox.offer(null), inbox.offer(null))
    }

    // --- the biometric gate --------------------------------------------------

    @Test
    fun `a share arriving behind the app lock waits for the unlock`() {
        val route = ShareRouter.route(request(), signedIn = true, locked = true)

        assertEquals(
            "the lock gate must not be a way into the create screen",
            ShareRoute.WaitForUnlock,
            route,
        )
    }

    @Test
    fun `the app lock is checked before the session`() {
        // Ordering matters: answering "signed out" while locked would tell an
        // onlooker something about the account state through the gate.
        assertEquals(
            ShareRoute.WaitForUnlock,
            ShareRouter.route(request(), signedIn = false, locked = true),
        )
    }

    @Test
    fun `a held share is not consumed by the lock`() {
        // Clearing it here is what would turn the gate into a share shredder.
        assertFalse(
            "a share waiting behind the lock must survive the wait",
            ShareRouter.consumes(ShareRoute.WaitForUnlock),
        )
    }

    @Test
    fun `the same share routes to the create screen once the gate comes down`() {
        val pending = request()

        assertEquals(ShareRoute.WaitForUnlock, ShareRouter.route(pending, signedIn = true, locked = true))
        assertEquals(
            ShareRoute.OpenCreate(github),
            ShareRouter.route(pending, signedIn = true, locked = false),
        )
    }

    // --- no session ----------------------------------------------------------

    @Test
    fun `a signed out share does not open a form that cannot be submitted`() {
        val route = ShareRouter.route(request(), signedIn = false, locked = false)

        assertFalse(
            "a create form with no session behind it cannot be saved: $route",
            route is ShareRoute.OpenCreate,
        )
        assertEquals(ShareRoute.RejectSignedOut, route)
    }

    @Test
    fun `a signed out share is finished with, not held`() {
        // The documented resolution of the open question in the brief: the
        // share is DROPPED, but visibly — RejectSignedOut is the caller's cue to
        // show a message. It is not parked for replay after a later sign-in,
        // because that means holding shared text across an external OAuth
        // round-trip and a possible process death, and because a create form
        // appearing minutes later, on its own, is a surprise the user cannot
        // connect to anything they did.
        assertTrue(
            "the caller must be free to clear a rejected share",
            ShareRouter.consumes(ShareRoute.RejectSignedOut),
        )
    }

    // --- text with no link ---------------------------------------------------

    @Test
    fun `shared text with no link opens an empty create form`() {
        // Today's behaviour, and worth keeping: the user asked for a mask, the
        // link just was not there to find.
        val route = ShareRouter.route(request(prefill = null), signedIn = true, locked = false)

        assertEquals(ShareRoute.OpenCreate(null), route)
    }

    // --- nothing pending -----------------------------------------------------

    @Test
    fun `no pending share is idle`() {
        assertEquals(ShareRoute.Idle, ShareRouter.route(null, signedIn = true, locked = false))
        assertEquals(ShareRoute.Idle, ShareRouter.route(null, signedIn = false, locked = true))
    }

    @Test
    fun `every route that finished with the share says so`() {
        // consumes() drives "clear the pending value". Only the held route
        // answers false; a new branch defaulting to false would silently replay
        // shares forever.
        listOf(
            ShareRoute.OpenCreate(github),
            ShareRoute.OpenCreate(null),
            ShareRoute.RejectSignedOut,
            ShareRoute.Idle,
        ).forEach { route ->
            assertTrue("$route leaves the share pending forever", ShareRouter.consumes(route))
        }
    }
}
