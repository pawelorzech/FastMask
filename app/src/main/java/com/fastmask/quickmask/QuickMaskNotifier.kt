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
        if (!canNotify()) {
            showToast(context.getString(R.string.quick_mask_copied, email))
            return
        }

        ensureChannel()

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
        val notification = NotificationCompat.Builder(context, QUICK_MASK_CHANNEL_ID)
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

        NotificationManagerCompat.from(context).notify(QUICK_MASK_NOTIFICATION_ID, notification)
    }

    fun showFailure(@StringRes messageRes: Int) {
        val message = context.getString(messageRes)
        if (!canNotify()) {
            showToast(message)
            return
        }

        ensureChannel()

        val notification = NotificationCompat.Builder(context, QUICK_MASK_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_quick_mask)
            .setContentTitle(context.getString(R.string.app_name))
            .setContentText(message)
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .setAutoCancel(true)
            .build()

        NotificationManagerCompat.from(context).notify(QUICK_MASK_NOTIFICATION_ID, notification)
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

    private fun canNotify(): Boolean {
        if (!NotificationManagerCompat.from(context).areNotificationsEnabled()) {
            return false
        }
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            return true
        }
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.POST_NOTIFICATIONS,
        ) == PackageManager.PERMISSION_GRANTED
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
