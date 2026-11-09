package com.fastmask.quickmask

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import androidx.annotation.StringRes
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.fastmask.R
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class QuickMaskNotifier @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private var channelReady = false

    /**
     * @param copied whether the address actually reached the clipboard. The
     *   notification (and its Toast fallback) only claims "copied" when it did;
     *   otherwise it says just that the mask exists, so it never lies about
     *   where the address went.
     */
    fun showCreated(id: String, copied: Boolean) {
        // Same neutral wording as the notification, and for the same reason: a
        // Toast is drawn over whatever is on screen, so spelling the address out
        // there would undo the decision to keep it off the display entirely. The
        // address is on the clipboard — that is where the user picks it up.
        val title = context.getString(R.string.quick_mask_created_title)
        val body = if (copied) context.getString(R.string.quick_mask_created_body) else null
        postOrToast(QuickMaskSlot.CREATED, fallbackMessage = body ?: title) {
            val openPendingIntent = createAppPendingIntent(context, QuickMaskSlot.CREATED)
            notification(title = title, text = body, contentIntent = openPendingIntent)
                .addAction(
                    R.drawable.ic_quick_mask,
                    context.getString(R.string.quick_mask_open),
                    openPendingIntent,
                )
                .addAction(
                    R.drawable.ic_quick_mask,
                    context.getString(R.string.quick_mask_undo),
                    createUndoPendingIntent(context, QuickMaskSlot.CREATED, id),
                )
                .build()
        }
    }

    fun showFailure(@StringRes messageRes: Int) {
        val message = context.getString(messageRes)
        postOrToast(QuickMaskSlot.FAILURE, fallbackMessage = message) {
            notification(title = context.getString(R.string.app_name), text = message).build()
        }
    }

    internal fun cancel(slot: QuickMaskSlot) {
        NotificationManagerCompat.from(context).cancel(slot.notificationId)
    }

    /**
     * A notification is visible wherever the user is looking, so it is the
     * default for BOTH outcomes; a Toast is drawn UNDER the notification
     * shade's blur — exactly where the user stands when they tap Undo — and
     * survives only as the fallback for a user who has denied notifications.
     */
    fun showUndoResult(success: Boolean) {
        val message = context.getString(
            if (success) R.string.quick_mask_undone else R.string.quick_mask_undo_failed,
        )
        postOrToast(QuickMaskSlot.UNDO_RESULT, fallbackMessage = message) {
            // The failure text tells the user to open FastMask and try again;
            // without a contentIntent, tapping the notification that says so did
            // nothing. Both outcomes get it — after a successful undo the app is
            // still where the mask list is — and it opens the launcher intent, so
            // the biometric gate stays in the path.
            notification(
                title = context.getString(R.string.app_name),
                text = message,
                contentIntent = createAppPendingIntent(context, QuickMaskSlot.UNDO_RESULT),
            ).build()
        }
    }

    /**
     * Release builds mark the app FLAG_SECURE; showing the masked address on the
     * lock screen would punch a privacy hole straight through that. Two
     * independent measures, because either one alone leaks:
     *
     * - The address is NOT in any notification. VISIBILITY_PRIVATE does not
     *   redact by default — SystemUI only redacts once the user has turned off
     *   "Show sensitive content on the lock screen"
     *   (Settings.Secure LOCK_SCREEN_ALLOW_PRIVATE_NOTIFICATIONS, which defaults
     *   to 1), so on a stock device the text would simply be readable by anyone
     *   standing next to the phone.
     * - VISIBILITY_SECRET (mirrored on the channel) keeps the notification off
     *   the lock screen entirely.
     *
     * The address is on the clipboard, and the app itself — behind FLAG_SECURE
     * and the biometric gate — is where it can be read.
     */
    private fun notification(
        title: String,
        text: String?,
        contentIntent: PendingIntent? = null,
    ): NotificationCompat.Builder =
        NotificationCompat.Builder(context, QuickMaskChannel.id)
            .setSmallIcon(R.drawable.ic_quick_mask)
            .setContentTitle(title)
            .setContentText(text)
            .setVisibility(NotificationCompat.VISIBILITY_SECRET)
            .setAutoCancel(true)
            .setContentIntent(contentIntent)

    // POST_NOTIFICATIONS may genuinely be missing on Android 13+, or notifications
    // switched off for the app; the user then gets the message via Toast.
    private fun canPost(manager: NotificationManagerCompat): Boolean {
        val postPermissionGranted = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.POST_NOTIFICATIONS,
        ) == PackageManager.PERMISSION_GRANTED
        return QuickMaskPolicy.canPostNotification(
            sdkInt = Build.VERSION.SDK_INT,
            notificationsEnabled = manager.areNotificationsEnabled(),
            postPermissionGranted = postPermissionGranted,
        )
    }

    @android.annotation.SuppressLint("MissingPermission") // guarded by canPost()
    private fun postOrToast(
        slot: QuickMaskSlot,
        fallbackMessage: String,
        buildNotification: () -> Notification,
    ) {
        val manager = NotificationManagerCompat.from(context)
        if (!canPost(manager)) {
            showToast(fallbackMessage)
            return
        }

        ensureChannel()
        manager.notify(slot.notificationId, buildNotification())
    }

    // createNotificationChannel is a no-op for an id the system already knows:
    // importance, lockscreenVisibility and description are read once at creation,
    // then owned by the user.
    private fun ensureChannel() {
        val manager = context.getSystemService(NotificationManager::class.java)
        if (!channelReady) {
            // Deleting an id the system does not know does nothing.
            QuickMaskChannel.staleIds
                .forEach { manager.deleteNotificationChannel(it) }
            channelReady = true
        }
        // Importance and lock-screen visibility are INDEPENDENT fields: a
        // heads-up banner is what the user sees on an unlocked phone, and
        // VISIBILITY_SECRET keeps the same notification off the lock screen.
        // At IMPORTANCE_DEFAULT the "Mask created" notification, and the Undo
        // that is the only way back from a mistaken mask, would never show.
        val channel = NotificationChannel(
            QuickMaskChannel.id,
            context.getString(R.string.quick_mask_channel_name),
            NotificationManager.IMPORTANCE_HIGH,
        ).apply {
            description = context.getString(R.string.quick_mask_channel_description)
            // Caps every notification on this channel, whatever the builder
            // asks for: nothing from quick-create reaches the lock screen.
            lockscreenVisibility = Notification.VISIBILITY_SECRET
        }
        manager.createNotificationChannel(channel)
    }

    private fun showToast(message: String) {
        val handler = Handler(Looper.getMainLooper())
        handler.post {
            Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
        }
    }
}
