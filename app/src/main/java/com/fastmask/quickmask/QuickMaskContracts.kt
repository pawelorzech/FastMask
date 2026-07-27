package com.fastmask.quickmask

import android.content.Context
import android.content.Intent
import com.fastmask.MainActivity

/**
 * The action carried by the launcher shortcut (see `res/xml/shortcuts.xml`).
 *
 * QuickMaskActivity is not exported, so nothing outside this package can start
 * it at all; this action is the second lock on the same door. Any launch that
 * does not carry it is treated as "just open the app", never as consent to
 * create a mask on the user's Fastmail account.
 */
internal const val ACTION_QUICK_MASK: String = "com.fastmask.action.QUICK_MASK"

/**
 * Legacy, pre-versioning notification channel id.
 *
 * New notifications do not post under this id anymore; it stays so
 * [QuickMaskChannel.staleIds] can remove the channel from devices that already
 * created it before versioned ids existed. See [QuickMaskChannel] for the id
 * the notifier uses now.
 */
internal const val QUICK_MASK_CHANNEL_ID: String = "quick_mask"

/**
 * Success and failure occupy separate notification slots.
 *
 * Both used to post under one id, so whichever came second replaced the first:
 * a failed create wiped the "Mask created" notification — and with it the Undo
 * action, the only way to take back a mask created by accident — while a
 * successful one silently erased an error the user had not read yet.
 */
internal const val QUICK_MASK_CREATED_NOTIFICATION_ID: Int = 7_301
internal const val QUICK_MASK_OPEN_REQUEST_CODE: Int = 7_302
internal const val QUICK_MASK_UNDO_REQUEST_CODE: Int = 7_303
internal const val QUICK_MASK_FAILURE_NOTIFICATION_ID: Int = 7_304

/**
 * The slot the result of "Undo" posts in.
 *
 * Its own id, because the two neighbouring slots are already spoken for: the
 * "mask created" notification is CANCELLED the moment Undo is tapped (reusing
 * its id would resurrect a dismissed notification), and the quick-create
 * failure slot carries a different failure the user may not have read yet.
 */
internal const val QUICK_MASK_UNDO_NOTIFICATION_ID: Int = 7_305

/**
 * Request code for the "open the app" intent on the undo-result notification.
 *
 * Its own code, not [QUICK_MASK_OPEN_REQUEST_CODE]: that one is reused with
 * FLAG_UPDATE_CURRENT by the "mask created" notification, and sharing it would
 * make two live notifications fight over one PendingIntent slot.
 */
internal const val QUICK_MASK_UNDO_OPEN_REQUEST_CODE: Int = 7_306
internal const val EXTRA_QUICK_MASK_ID: String = "quick_mask_id"

internal fun createAppLaunchIntent(context: Context): Intent {
    val launchIntent = context.packageManager.getLaunchIntentForPackage(context.packageName)
        ?: Intent(context, MainActivity::class.java)
    return launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
}
