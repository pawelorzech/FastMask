package com.fastmask.quickmask

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.fastmask.R
import com.fastmask.testutil.awaitNonNull
import com.fastmask.testutil.string
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * What the platform actually ends up holding after the quick-mask notifier has run:
 * the live channel is a heads-up (IMPORTANCE_HIGH) channel that stays off the lock
 * screen, every superseded channel id is gone, and the "Mask created" notification
 * carries no address and offers Open and Undo.
 *
 * Replaces the source-grep `QuickMaskChannelImportanceTest` (unit); the pure id/version
 * arithmetic of [QuickMaskChannel] stays covered by its own unit tests.
 */
@RunWith(AndroidJUnit4::class)
class QuickMaskNotifierChannelTest {

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val manager = context.getSystemService(NotificationManager::class.java)

    @Before
    fun grantPostingAndClearTheSlot() {
        InstrumentationRegistry.getInstrumentation().uiAutomation.grantRuntimePermission(
            context.packageName,
            "android.permission.POST_NOTIFICATIONS",
        )
        manager.cancel(QuickMaskSlot.CREATED.notificationId)
    }

    @Test
    fun theLiveChannelIsHeadsUpAndStaysOffTheLockScreen() {
        QuickMaskNotifier(context).showCreated(id = "mask-1", copied = true)

        val channel = manager.getNotificationChannel(QuickMaskChannel.id)
        assertNotNull("the notifier must create channel ${QuickMaskChannel.id}", channel)
        assertEquals(
            "at IMPORTANCE_DEFAULT the Undo action never becomes visible",
            NotificationManager.IMPORTANCE_HIGH,
            channel.importance,
        )
        assertEquals(
            "a heads-up channel must not put quick-create notifications on the lock screen",
            Notification.VISIBILITY_SECRET,
            channel.lockscreenVisibility,
        )
    }

    @Test
    fun supersededChannelsAreRemovedWhenTheNotifierRuns() {
        QuickMaskChannel.staleIds.forEach { staleId ->
            manager.createNotificationChannel(
                NotificationChannel(staleId, "stale $staleId", NotificationManager.IMPORTANCE_DEFAULT),
            )
            assertNotNull("precondition: $staleId exists", manager.getNotificationChannel(staleId))
        }

        QuickMaskNotifier(context).showCreated(id = "mask-1", copied = true)

        QuickMaskChannel.staleIds.forEach { staleId ->
            assertNull("$staleId must be deleted", manager.getNotificationChannel(staleId))
        }
        assertNotNull(
            "deleting the stale ids must never take the live channel with it",
            manager.getNotificationChannel(QuickMaskChannel.id),
        )
    }

    @Test
    fun theCreatedNotificationCarriesNoAddressAndOffersOpenAndUndo() {
        QuickMaskNotifier(context).showCreated(id = "mask-1", copied = true)

        val posted = awaitNonNull("the created notification never reached the notification manager") {
            manager.activeNotifications
                .firstOrNull { it.id == QuickMaskSlot.CREATED.notificationId }
                ?.notification
        }

        val title = posted.extras.getCharSequence(Notification.EXTRA_TITLE)?.toString()
        val text = posted.extras.getCharSequence(Notification.EXTRA_TEXT)?.toString()
        assertEquals(string(R.string.quick_mask_created_title), title)
        assertEquals(string(R.string.quick_mask_created_body), text)
        assertFalse("no address on a notification drawn over other apps", title.orEmpty().contains('@'))
        assertFalse("no address on a notification drawn over other apps", text.orEmpty().contains('@'))
        assertEquals(Notification.VISIBILITY_SECRET, posted.visibility)

        val actionTitles = posted.actions.map { it.title.toString() }
        assertEquals(
            listOf(string(R.string.quick_mask_open), string(R.string.quick_mask_undo)),
            actionTitles,
        )
    }
}
