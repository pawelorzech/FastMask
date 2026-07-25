package com.fastmask.quickmask

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
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

    fun showCreated(id: String, email: String) {
        // The address is already on the clipboard by now, so the fallback still
        // tells the user what they got even when notifications are unavailable.
        postOrToast(fallbackMessage = context.getString(R.string.quick_mask_copied, email)) {
            val openPendingIntent = PendingIntent.getActivity(
                context,
                QUICK_MASK_OPEN_REQUEST_CODE,
                createAppLaunchIntent(context),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
            val undoIntent = Intent(context, QuickMaskUndoReceiver::class.java)
                .putExtra(EXTRA_QUICK_MASK_ID, id)
                .putExtra(EXTRA_NOTIFICATION_ID, QUICK_MASK_NOTIFICATION_ID)
            val undoPendingIntent = PendingIntent.getBroadcast(
                context,
                QUICK_MASK_UNDO_REQUEST_CODE,
                undoIntent,
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )

            // Release builds mark the app FLAG_SECURE; showing the masked address
            // on the lock screen would punch a privacy hole straight through that.
            NotificationCompat.Builder(context, QUICK_MASK_CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_quick_mask)
                .setContentTitle(context.getString(R.string.quick_mask_created_title))
                .setContentText(email)
                .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
                .setAutoCancel(true)
                .setContentIntent(openPendingIntent)
                .addAction(
                    R.drawable.ic_quick_mask,
                    context.getString(R.string.quick_mask_open),
                    openPendingIntent,
                )
                .addAction(
                    R.drawable.ic_quick_mask,
                    context.getString(R.string.quick_mask_undo),
                    undoPendingIntent,
                )
                .build()
        }
    }

    fun showFailure(@StringRes messageRes: Int) {
        val message = context.getString(messageRes)
        postOrToast(fallbackMessage = message) {
            NotificationCompat.Builder(context, QUICK_MASK_CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_quick_mask)
                .setContentTitle(context.getString(R.string.app_name))
                .setContentText(message)
                .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
                .setAutoCancel(true)
                .build()
        }
    }

    fun cancel(notificationId: Int) {
        NotificationManagerCompat.from(context).cancel(notificationId)
    }

    fun showUndoResult(success: Boolean) {
        val messageRes = if (success) {
            R.string.quick_mask_undone
        } else {
            R.string.quick_mask_undo_failed
        }
        showToast(context.getString(messageRes))
    }

    /**
     * The one place that calls `notify`. The POST_NOTIFICATIONS check is inlined
     * here on purpose — both because the permission may genuinely be missing on
     * Android 13+ (then the user still gets the address via Toast) and because a
     * check hidden behind a helper is invisible to lint's MissingPermission
     * data flow. The notification itself is only built once we know it can be
     * posted, so the fallback path allocates no PendingIntents.
     */
    private fun postOrToast(fallbackMessage: String, buildNotification: () -> Notification) {
        val manager = NotificationManagerCompat.from(context)
        val postPermissionGranted = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.POST_NOTIFICATIONS,
        ) == PackageManager.PERMISSION_GRANTED
        val canPost = QuickMaskPolicy.canPostNotification(
            sdkInt = Build.VERSION.SDK_INT,
            notificationsEnabled = manager.areNotificationsEnabled(),
            postPermissionGranted = postPermissionGranted,
        )
        if (!canPost) {
            showToast(fallbackMessage)
            return
        }

        ensureChannel()
        manager.notify(QUICK_MASK_NOTIFICATION_ID, buildNotification())
    }

    private fun ensureChannel() {
        val manager = context.getSystemService(NotificationManager::class.java)
        val channel = NotificationChannel(
            QUICK_MASK_CHANNEL_ID,
            context.getString(R.string.quick_mask_channel_name),
            NotificationManager.IMPORTANCE_DEFAULT,
        ).apply {
            description = context.getString(R.string.quick_mask_channel_description)
            lockscreenVisibility = Notification.VISIBILITY_PRIVATE
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
