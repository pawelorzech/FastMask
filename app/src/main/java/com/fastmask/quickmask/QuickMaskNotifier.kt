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
    private var channelReady = false

    fun showCreated(id: String) {
        // Same neutral wording as the notification, and for the same reason: a
        // Toast is drawn over whatever is on screen, so spelling the address out
        // there would undo the decision to keep it off the display entirely. The
        // address is on the clipboard — that is where the user picks it up.
        postOrToast(
            notificationId = QuickMaskPolicy.notificationId(success = true),
            fallbackMessage = context.getString(R.string.quick_mask_created_body),
        ) {
            val openPendingIntent = PendingIntent.getActivity(
                context,
                QUICK_MASK_OPEN_REQUEST_CODE,
                createAppLaunchIntent(context),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
            val undoIntent = Intent(context, QuickMaskUndoReceiver::class.java)
                .putExtra(EXTRA_QUICK_MASK_ID, id)
            val undoPendingIntent = PendingIntent.getBroadcast(
                context,
                QUICK_MASK_UNDO_REQUEST_CODE,
                undoIntent,
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )

            // Release builds mark the app FLAG_SECURE; showing the masked address
            // on the lock screen would punch a privacy hole straight through
            // that. Two independent measures, because either one alone leaks:
            //
            // - The address is NOT in the notification. VISIBILITY_PRIVATE does
            //   not redact by default — SystemUI only redacts once the user has
            //   turned off "Show sensitive content on the lock screen"
            //   (Settings.Secure LOCK_SCREEN_ALLOW_PRIVATE_NOTIFICATIONS, which
            //   defaults to 1), so on a stock device the text would simply be
            //   readable by anyone standing next to the phone.
            // - VISIBILITY_SECRET (mirrored on the channel) keeps the
            //   notification off the lock screen entirely.
            //
            // The address is on the clipboard, and the app itself — behind
            // FLAG_SECURE and the biometric gate — is where it can be read.
            NotificationCompat.Builder(context, QuickMaskChannel.id)
                .setSmallIcon(R.drawable.ic_quick_mask)
                .setContentTitle(context.getString(R.string.quick_mask_created_title))
                .setContentText(context.getString(R.string.quick_mask_created_body))
                .setVisibility(NotificationCompat.VISIBILITY_SECRET)
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
        postOrToast(
            notificationId = QuickMaskPolicy.notificationId(success = false),
            fallbackMessage = message,
        ) {
            NotificationCompat.Builder(context, QuickMaskChannel.id)
                .setSmallIcon(R.drawable.ic_quick_mask)
                .setContentTitle(context.getString(R.string.app_name))
                .setContentText(message)
                .setVisibility(NotificationCompat.VISIBILITY_SECRET)
                .setAutoCancel(true)
                .build()
        }
    }

    fun cancel(notificationId: Int) {
        NotificationManagerCompat.from(context).cancel(notificationId)
    }

    fun showUndoResult(success: Boolean) {
        val manager = NotificationManagerCompat.from(context)
        val postPermissionGranted = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.POST_NOTIFICATIONS,
        ) == PackageManager.PERMISSION_GRANTED
        val canPostNotification = QuickMaskPolicy.canPostNotification(
            sdkInt = Build.VERSION.SDK_INT,
            notificationsEnabled = manager.areNotificationsEnabled(),
            postPermissionGranted = postPermissionGranted,
        )
        val feedback = QuickMaskUndoPolicy.feedback(
            success = success,
            canPostNotification = canPostNotification,
        )
        val message = context.getString(
            when (feedback.message) {
                UndoFeedbackMessage.UNDONE -> R.string.quick_mask_undone
                UndoFeedbackMessage.UNDO_FAILED -> R.string.quick_mask_undo_failed
            },
        )
        when (feedback.channel) {
            UndoFeedbackChannel.TOAST -> showToast(message)
            UndoFeedbackChannel.NOTIFICATION -> {
                // The policy only asks for a notification together with a slot
                // to post it in, so the null arm is unreachable today. It is
                // written out rather than forced with !! because the failure it
                // would cause — the one message the user must not miss going
                // nowhere — is worse than a Toast they can still read.
                when (val notificationId = feedback.notificationId) {
                    null -> showToast(message)
                    else -> {
                        ensureChannel()
                        // The failure text tells the user to open FastMask and
                        // try again; without a contentIntent, tapping the
                        // notification that says so did nothing. Both outcomes
                        // get it — after a successful undo the app is still
                        // where the mask list is — and it opens the launcher
                        // intent, so the biometric gate stays in the path.
                        val openPendingIntent = PendingIntent.getActivity(
                            context,
                            QUICK_MASK_UNDO_OPEN_REQUEST_CODE,
                            createAppLaunchIntent(context),
                            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
                        )
                        manager.notify(
                            notificationId,
                            NotificationCompat.Builder(context, QuickMaskChannel.id)
                                .setSmallIcon(R.drawable.ic_quick_mask)
                                .setContentTitle(context.getString(R.string.app_name))
                                .setContentText(message)
                                .setVisibility(NotificationCompat.VISIBILITY_SECRET)
                                .setAutoCancel(true)
                                .setContentIntent(openPendingIntent)
                                .build(),
                        )
                    }
                }
            }
        }
    }

    /**
     * The one place that calls `notify`. The POST_NOTIFICATIONS check is inlined
     * here on purpose — both because the permission may genuinely be missing on
     * Android 13+ (then the user still gets the message via Toast) and because a
     * check hidden behind a helper is invisible to lint's MissingPermission
     * data flow. The notification itself is only built once we know it can be
     * posted, so the fallback path allocates no PendingIntents.
     *
     * @param notificationId the caller's own slot — see [QuickMaskPolicy.notificationId].
     */
    private fun postOrToast(
        notificationId: Int,
        fallbackMessage: String,
        buildNotification: () -> Notification,
    ) {
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
        manager.notify(notificationId, buildNotification())
    }

    // createNotificationChannel is a no-op for an id the system already knows:
    // QuickMaskChannel exists because importance, lockscreenVisibility and
    // description are read once at creation, then owned by the user. When one
    // of those fields changes in source, bump QuickMaskChannel.VERSION so the
    // app creates a fresh id, and let deleteNotificationChannel clear the
    // superseded ones instead of leaving dead duplicates in settings.
    private fun ensureChannel() {
        val manager = context.getSystemService(NotificationManager::class.java)
        if (!channelReady) {
            // Superseded channels would otherwise sit in system settings forever
            // as dead duplicates. Deleting an id the system does not know does
            // nothing, so this is safe on a fresh install.
            QuickMaskChannel.staleIds(QuickMaskChannel.VERSION)
                .forEach { manager.deleteNotificationChannel(it) }
            channelReady = true
        }
        // QuickMaskChannel is deliberately free of Android types, so the
        // importance arrives here as a plain Int and lint's @IntDef cannot see
        // that it is one of the platform constants. The check the annotation
        // would have given us lives in QuickMaskChannelImportanceTest, which
        // asserts QuickMaskChannel.IMPORTANCE == IMPORTANCE_HIGH == 4 and fails
        // the build the moment the two drift apart.
        @Suppress("WrongConstant")
        val channel = NotificationChannel(
            QuickMaskChannel.id,
            context.getString(R.string.quick_mask_channel_name),
            QuickMaskChannel.IMPORTANCE,
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
