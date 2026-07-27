package com.fastmask.domain.share

import java.util.concurrent.atomic.AtomicLong

/**
 * One delivery of shared text into FastMask.
 *
 * [deliveryId] exists because the routing state used to be held in a Compose
 * `mutableStateOf`, whose default `structuralEqualityPolicy()` treats an equal
 * value as "no change". A data class carrying only [prefill] therefore made the
 * SECOND share of the same link a no-op: the state never reported a change, so
 * the effect that navigates never re-ran. A monotonically increasing id makes
 * every delivery structurally distinct, whatever the payload.
 *
 * @param prefill parsed link, or null when the shared text carried none — that
 *   is still a share, and it opens an EMPTY create form.
 * @param deliveryId strictly increasing per [ShareInbox]; never reused.
 */
data class ShareRequest(
    val prefill: SharePrefill?,
    val deliveryId: Long,
)

/**
 * What the app should do with a [ShareRequest].
 *
 * Every branch is explicit because the bug being fixed was a silent one: a
 * share that matched no condition fell through to "clear it and say nothing",
 * so the user saw the app come to the front unchanged and assumed a mis-tap.
 */
sealed interface ShareRoute {

    /** Navigate to the create screen, prefilled from [prefill] (null = empty form). */
    data class OpenCreate(val prefill: SharePrefill?) : ShareRoute

    /**
     * The biometric app-lock gate is up. The share is HELD, not dropped, and
     * re-routed once the gate comes down. The gate is deliberate: a share must
     * never be a way past the lock.
     */
    data object WaitForUnlock : ShareRoute

    /**
     * No session. The create form cannot be submitted, so it must not open;
     * the user is told instead of being left with silence.
     */
    data object RejectSignedOut : ShareRoute

    /** Nothing pending. */
    data object Idle : ShareRoute
}

/**
 * Hands out [ShareRequest]s, one per delivery.
 *
 * Deliberately not an object: the counter is per-Activity instance, and tests
 * need their own.
 */
class ShareInbox {

    private val nextDeliveryId = AtomicLong(0)

    /** @return a request whose `deliveryId` is greater than every previous one. */
    fun offer(prefill: SharePrefill?): ShareRequest =
        ShareRequest(
            prefill = prefill,
            deliveryId = nextDeliveryId.incrementAndGet(),
        )
}

/**
 * The share routing decision, free of Android and Compose types.
 *
 * Pulled out of `MainActivity` because the decision there was keyed on
 * `startDestination` — the destination computed ONCE in `onCreate`. A process
 * that started signed out (start destination `WELCOME`) and signed in without
 * leaving kept that value for its whole life, so every later share was dropped
 * in silence. The routing input is the LIVE session state, and nothing here
 * knows that a "start destination" exists.
 */
object ShareRouter {

    /**
     * @param request the pending delivery, or null when there is none.
     * @param signedIn LIVE session state — whether a create form could be
     *   submitted right now. Demo mode counts as signed in: `isLoggedIn()`
     *   reports true there and the create screen is reachable and usable.
     *   Never pass a launch-time snapshot.
     * @param locked whether the biometric app-lock gate is currently covering
     *   the content.
     */
    fun route(request: ShareRequest?, signedIn: Boolean, locked: Boolean): ShareRoute =
        when {
            request == null -> ShareRoute.Idle
            locked -> ShareRoute.WaitForUnlock
            !signedIn -> ShareRoute.RejectSignedOut
            else -> ShareRoute.OpenCreate(request.prefill)
        }

    /**
     * Whether [route] finished with the request — i.e. the caller may clear it.
     *
     * A held share ([ShareRoute.WaitForUnlock]) must survive: clearing it would
     * turn the lock gate into a share shredder.
     */
    fun consumes(route: ShareRoute): Boolean =
        when (route) {
            is ShareRoute.OpenCreate -> true
            ShareRoute.WaitForUnlock -> false
            ShareRoute.RejectSignedOut -> true
            ShareRoute.Idle -> true
        }
}
