package com.fastmask.domain.share

import java.util.concurrent.atomic.AtomicLong

/**
 * One delivery of shared text into FastMask.
 *
 * [deliveryId] makes every delivery structurally distinct: Compose
 * `mutableStateOf` treats an equal value as "no change", so without it a second
 * share of the same link would not re-run the navigating effect.
 *
 * @param prefill null when the shared text carried no link: that is still a
 *   share, and it opens an EMPTY create form.
 */
data class ShareRequest(
    val prefill: SharePrefill?,
    val deliveryId: Long,
)

/**
 * What the app should do with a [ShareRequest].
 *
 * Every branch is explicit: a share must never fall through to "clear it and
 * say nothing".
 */
sealed interface ShareRoute {

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

    data object Idle : ShareRoute
}

class ShareInbox {

    private val nextDeliveryId = AtomicLong(0)

    fun offer(prefill: SharePrefill?): ShareRequest =
        ShareRequest(
            prefill = prefill,
            deliveryId = nextDeliveryId.incrementAndGet(),
        )
}

object ShareRouter {

    // The routing input is the LIVE session state, never a launch-time snapshot.
    fun route(request: ShareRequest?, signedIn: Boolean, locked: Boolean): ShareRoute =
        when {
            request == null -> ShareRoute.Idle
            locked -> ShareRoute.WaitForUnlock
            !signedIn -> ShareRoute.RejectSignedOut
            else -> ShareRoute.OpenCreate(request.prefill)
        }

    /**
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
