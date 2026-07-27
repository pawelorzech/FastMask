package com.fastmask.quickmask

/**
 * At-most-once gate over a STREAM of session snapshots.
 *
 * [QuickMaskPolicy.shouldRequestNotificationPermission] answers "does this
 * snapshot warrant the prompt?"; it cannot answer "has this process already
 * asked?", because the persisted flag is written asynchronously and a second
 * snapshot can arrive before that write lands. MainActivity evaluated the
 * predicate exactly once, in `onCreate`, off the START DESTINATION — so a user
 * who launched signed out and signed in during the same session (welcome →
 * login → list) was never asked, and never saw the quick-create confirmation
 * that carries the only "Undo" action.
 *
 * The fix is to feed this gate every session-state change and let it decide;
 * it owes the caller exactly one `true` per process.
 */
internal class NotificationPermissionGate(private val sdkInt: Int) {

    private var prompted = false

    /**
     * @return true at most once, for the first snapshot that warrants the prompt.
     */
    fun shouldPrompt(
        permissionGranted: Boolean,
        alreadyAsked: Boolean,
        signedIn: Boolean,
        locked: Boolean,
        demoMode: Boolean,
    ): Boolean {
        if (prompted) return false
        val warranted = QuickMaskPolicy.shouldRequestNotificationPermission(
            sdkInt = sdkInt,
            permissionGranted = permissionGranted,
            alreadyAsked = alreadyAsked,
            signedIn = signedIn,
            locked = locked,
            demoMode = demoMode,
        )
        if (warranted) prompted = true
        return warranted
    }
}
