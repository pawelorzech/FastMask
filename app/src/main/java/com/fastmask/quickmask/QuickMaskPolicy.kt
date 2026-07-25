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
}
