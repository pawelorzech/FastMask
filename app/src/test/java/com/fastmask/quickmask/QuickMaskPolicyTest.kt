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

    // --- what may create a mask ---------------------------------------------
    //
    // QuickMaskActivity used to create one for ANY intent that reached it,
    // while the manifest exported it with no intent-filter — a write to the
    // user's Fastmail account that any installed app could trigger in a loop.
    // The activity is unexported now; this predicate is the second lock, so
    // that "someone started this activity" can never by itself mean "create".

    @Test
    fun `only our own shortcut action may create a mask`() {
        assertTrue(QuickMaskPolicy.isQuickCreateLaunch(ACTION_QUICK_MASK))
    }

    @Test
    fun `any other launch is not consent to create`() {
        assertFalse(QuickMaskPolicy.isQuickCreateLaunch(null))
        assertFalse(QuickMaskPolicy.isQuickCreateLaunch(""))
        assertFalse(QuickMaskPolicy.isQuickCreateLaunch("android.intent.action.VIEW"))
        assertFalse(QuickMaskPolicy.isQuickCreateLaunch("android.intent.action.MAIN"))
        assertFalse(QuickMaskPolicy.isQuickCreateLaunch("com.fastmask.action.quick_mask"))
    }

    @Test
    fun `the shortcut action is the one declared in shortcuts xml`() {
        // The static shortcut carries this literal; a rename on one side only
        // would silently downgrade the shortcut to "just open the app".
        assertTrue(
            "shortcuts.xml does not declare $ACTION_QUICK_MASK",
            java.io.File("src/main/res/xml/shortcuts.xml").readText()
                .contains("android:action=\"$ACTION_QUICK_MASK\""),
        )
    }

    // --- asking for POST_NOTIFICATIONS --------------------------------------
    //
    // The permission was declared and never requested, so on Android 13+ it is
    // denied on every fresh install — and the quick-create confirmation, the
    // only place the "Undo" action exists, never appeared for anyone.

    @Test
    fun `a signed-in user on Android 13 plus is asked once`() {
        assertTrue(
            QuickMaskPolicy.shouldRequestNotificationPermission(
                sdkInt = current,
                permissionGranted = false,
                alreadyAsked = false,
                signedIn = true,
            )
        )
    }

    @Test
    fun `nobody is asked twice, and nobody is asked for what they already granted`() {
        assertFalse(
            QuickMaskPolicy.shouldRequestNotificationPermission(
                sdkInt = current,
                permissionGranted = false,
                alreadyAsked = true,
                signedIn = true,
            )
        )
        assertFalse(
            QuickMaskPolicy.shouldRequestNotificationPermission(
                sdkInt = current,
                permissionGranted = true,
                alreadyAsked = false,
                signedIn = true,
            )
        )
    }

    @Test
    fun `a signed-out user is not asked, and neither is Android 12`() {
        // No session means no mask to confirm; below 13 the permission is
        // granted at install time and launching the contract shows no dialog.
        assertFalse(
            QuickMaskPolicy.shouldRequestNotificationPermission(
                sdkInt = current,
                permissionGranted = false,
                alreadyAsked = false,
                signedIn = false,
            )
        )
        assertFalse(
            QuickMaskPolicy.shouldRequestNotificationPermission(
                sdkInt = preTiramisu,
                permissionGranted = false,
                alreadyAsked = false,
                signedIn = true,
            )
        )
    }
}
