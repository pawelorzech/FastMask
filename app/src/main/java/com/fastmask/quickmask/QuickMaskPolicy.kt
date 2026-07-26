package com.fastmask.quickmask

/**
 * Platform-version decisions behind the quick-create entry points, kept free of
 * Android types so the thresholds themselves stay unit-testable.
 *
 * Both rules here are about *not* silently losing the created address: the tile
 * copies a masked address and then tells the user about it. If the telling path
 * is unavailable (no POST_NOTIFICATIONS on 33+, or notifications switched off
 * for the app), the caller must fall back to a Toast instead of dropping the
 * message on the floor.
 */
internal object QuickMaskPolicy {

    /** [android.os.Build.VERSION_CODES.TIRAMISU] — POST_NOTIFICATIONS became runtime-granted. */
    const val NOTIFICATION_PERMISSION_SDK: Int = 33

    /**
     * Whether `NotificationManagerCompat.notify` may be called at all.
     *
     * @param sdkInt running platform level.
     * @param notificationsEnabled app-level notification switch (user can kill it on any version).
     * @param postPermissionGranted result of `checkSelfPermission(POST_NOTIFICATIONS)`;
     *   meaningless below [NOTIFICATION_PERMISSION_SDK], where the permission does
     *   not exist and the check answers DENIED for every app.
     */
    fun canPostNotification(
        sdkInt: Int,
        notificationsEnabled: Boolean,
        postPermissionGranted: Boolean,
    ): Boolean {
        if (!notificationsEnabled) return false
        return sdkInt < NOTIFICATION_PERMISSION_SDK || postPermissionGranted
    }

    /**
     * The notification slot a quick-create message belongs in.
     *
     * `notify` replaces any notification already posted under the same id, so
     * sharing one between the two outcomes made them cannibalise each other:
     * a failure landing after a success took down the "Mask created"
     * notification together with its Undo action — the only route back from a
     * mask created by mistake — and a success posted while an error was still
     * on screen made the error vanish unread. Two ids, two independent slots.
     *
     * @param success whether the mask was created.
     */
    fun notificationId(success: Boolean): Int =
        if (success) QUICK_MASK_CREATED_NOTIFICATION_ID else QUICK_MASK_FAILURE_NOTIFICATION_ID

    /**
     * Whether an incoming launch of `QuickMaskActivity` may create a mask.
     *
     * The activity used to create one unconditionally in `onCreate`, which —
     * combined with `exported="true"` and no intent-filter — let any app on the
     * device drive FastMask into minting real masks on the user's Fastmail
     * account and overwriting the clipboard, with no interaction at all. The
     * manifest now keeps the activity unexported; this check makes "create"
     * conditional on OUR OWN action, so an unrecognized launch degrades into
     * opening the app instead of writing to the account.
     *
     * @param action the launching intent's action.
     */
    fun isQuickCreateLaunch(action: String?): Boolean = action == ACTION_QUICK_MASK

    /**
     * Whether to ask for POST_NOTIFICATIONS.
     *
     * The permission is declared and never requested, so on Android 13+ it is
     * DENIED on a fresh install and the quick-create confirmation — the only
     * place the "Undo" action exists — never appears. Asked at most once, and
     * only where it means something: a signed-in user looking at their masks,
     * not a cold welcome screen.
     *
     * @param sdkInt running platform level.
     * @param permissionGranted result of `checkSelfPermission(POST_NOTIFICATIONS)`.
     * @param alreadyAsked whether this install has already shown the prompt.
     * @param signedIn whether there is a session to create masks in.
     */
    fun shouldRequestNotificationPermission(
        sdkInt: Int,
        permissionGranted: Boolean,
        alreadyAsked: Boolean,
        signedIn: Boolean,
    ): Boolean {
        if (sdkInt < NOTIFICATION_PERMISSION_SDK) return false
        if (permissionGranted || alreadyAsked) return false
        return signedIn
    }
}
