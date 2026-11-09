package com.fastmask.quickmask

import android.os.Build

/**
 * Both rules here are about *not* silently losing the created address: the tile
 * copies a masked address and then tells the user about it. If the telling path
 * is unavailable (no POST_NOTIFICATIONS on 33+, or notifications switched off
 * for the app), the caller must fall back to a Toast.
 */
internal object QuickMaskPolicy {

    /** POST_NOTIFICATIONS became runtime-granted on Android 13. */
    const val NOTIFICATION_PERMISSION_SDK: Int = Build.VERSION_CODES.TIRAMISU

    /**
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
     * On Android 13+ the permission is DENIED on a fresh install, and without it
     * the quick-create confirmation — the only place the "Undo" action exists —
     * never appears. Asked at most once, and only where it means something: a
     * signed-in user looking at their masks, not a cold welcome screen.
     *
     * @param locked a system dialog stacked on the biometric gate asks about a
     *   feature the user cannot see.
     * @param demoMode the confirmation notification this permission exists for
     *   can never be posted in demo, and a denial there is remembered by the
     *   platform and makes the later real request harder to surface.
     */
    fun shouldRequestNotificationPermission(
        sdkInt: Int,
        permissionGranted: Boolean,
        alreadyAsked: Boolean,
        signedIn: Boolean,
        locked: Boolean,
        demoMode: Boolean,
    ): Boolean {
        if (sdkInt < NOTIFICATION_PERMISSION_SDK) return false
        if (permissionGranted || alreadyAsked) return false
        if (locked) return false
        if (demoMode) return false
        return signedIn
    }
}
