package com.fastmask.quickmask

import android.app.Notification
import android.app.NotificationManager
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Instrumented because the thing under test only exists once the platform has
 * the notification: whether the posted `Notification` carries a contentIntent
 * is a property of the object SystemUI receives, not of the builder call.
 *
 * The failure text is an instruction — *"Could not delete the mask. Open
 * FastMask and try again."* — and until this was fixed, tapping the
 * notification that says so did nothing at all.
 *
 * Nothing is cancelled at the end on purpose: the notification is left posted
 * so the shade can be inspected by hand after a run. Each test clears the slot
 * on the way in instead.
 */
@RunWith(AndroidJUnit4::class)
class QuickMaskUndoNotificationTest {

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val manager = context.getSystemService(NotificationManager::class.java)
    private val notifier = QuickMaskNotifier(context)

    @Before
    fun clearSlot() {
        InstrumentationRegistry.getInstrumentation().uiAutomation.grantRuntimePermission(
            context.packageName,
            "android.permission.POST_NOTIFICATIONS",
        )
        manager.cancel(QUICK_MASK_UNDO_NOTIFICATION_ID)
    }

    private fun postedUndoResult(): Notification {
        repeat(50) {
            val posted = manager.activeNotifications
                .firstOrNull { it.id == QUICK_MASK_UNDO_NOTIFICATION_ID }
            if (posted != null) return posted.notification
            Thread.sleep(100)
        }
        throw AssertionError("the undo result never reached the notification manager")
    }

    @Test
    fun aFailedUndoCanBeTapped() {
        notifier.showUndoResult(success = false)

        assertNotNull(
            "the undo failure tells the user to open FastMask and carries no contentIntent — " +
                "tapping it does nothing",
            postedUndoResult().contentIntent,
        )
    }

    @Test
    fun aSuccessfulUndoCanBeTappedToo() {
        notifier.showUndoResult(success = true)

        assertNotNull(
            "after a successful undo the app is still where the mask list is",
            postedUndoResult().contentIntent,
        )
    }

    @Test
    fun theUndoResultStaysOffTheLockScreen() {
        notifier.showUndoResult(success = false)

        assertEquals(
            "the undo result must stay off the lock screen like every other quick-mask " +
                "notification",
            Notification.VISIBILITY_SECRET,
            postedUndoResult().visibility,
        )
    }

    @Test
    fun theUndoResultCarriesNoAddress() {
        notifier.showUndoResult(success = false)

        val extras = postedUndoResult().extras
        val text = buildString {
            append(extras.getCharSequence(Notification.EXTRA_TITLE) ?: "")
            append(' ')
            append(extras.getCharSequence(Notification.EXTRA_TEXT) ?: "")
        }

        assertFalse("the undo result must never show a masked address: $text", text.contains('@'))
    }
}
