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
internal const val EXTRA_QUICK_MASK_ID: String = "quick_mask_id"
internal const val EXTRA_NOTIFICATION_ID: String = "notification_id"

internal fun createAppLaunchIntent(context: Context): Intent {
    val launchIntent = context.packageManager.getLaunchIntentForPackage(context.packageName)
        ?: Intent(context, MainActivity::class.java)
    return launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
}
