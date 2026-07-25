package com.fastmask.quickmask

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The quick-create tile copies a masked address and then has to tell the user
 * about it. Posting a notification is the nice way; when the platform will not
 * let us, the caller owes the user a Toast instead. Getting this predicate
 * wrong either drops the message silently or — on Android 13+ without the
 * runtime permission — throws from `notify`.
 */
class QuickMaskPolicyTest {

    private val preTiramisu = 32
    private val tiramisu = QuickMaskPolicy.NOTIFICATION_PERMISSION_SDK
    private val current = 36

    @Test
    fun `below Android 13 the runtime permission is irrelevant`() {
        // checkSelfPermission answers DENIED there for every app: the permission
        // simply does not exist, so honouring it would kill all notifications.
        assertTrue(
            QuickMaskPolicy.canPostNotification(
                sdkInt = preTiramisu,
                notificationsEnabled = true,
                postPermissionGranted = false,
            )
        )
    }

    @Test
    fun `from Android 13 a denied permission means no notify call`() {
        assertFalse(
            QuickMaskPolicy.canPostNotification(
                sdkInt = tiramisu,
                notificationsEnabled = true,
                postPermissionGranted = false,
            )
        )
        assertFalse(
            QuickMaskPolicy.canPostNotification(
                sdkInt = current,
                notificationsEnabled = true,
                postPermissionGranted = false,
            )
        )
    }

    @Test
    fun `from Android 13 a granted permission allows the notification`() {
        assertTrue(
            QuickMaskPolicy.canPostNotification(
                sdkInt = current,
                notificationsEnabled = true,
                postPermissionGranted = true,
            )
        )
    }

    @Test
    fun `notifications switched off for the app beat everything else`() {
        // The user can kill the app's notifications on any version; a granted
        // POST_NOTIFICATIONS does not resurrect them.
        assertFalse(
            QuickMaskPolicy.canPostNotification(
                sdkInt = preTiramisu,
                notificationsEnabled = false,
                postPermissionGranted = true,
            )
        )
        assertFalse(
            QuickMaskPolicy.canPostNotification(
                sdkInt = current,
                notificationsEnabled = false,
                postPermissionGranted = true,
            )
        )
    }
}
