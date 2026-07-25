package com.fastmask.quickmask

import android.content.Context
import android.content.Intent
import com.fastmask.MainActivity

internal const val QUICK_MASK_CHANNEL_ID: String = "quick_mask"
internal const val QUICK_MASK_NOTIFICATION_ID: Int = 7_301
internal const val QUICK_MASK_OPEN_REQUEST_CODE: Int = 7_302
internal const val QUICK_MASK_UNDO_REQUEST_CODE: Int = 7_303
internal const val EXTRA_QUICK_MASK_ID: String = "quick_mask_id"
internal const val EXTRA_NOTIFICATION_ID: String = "notification_id"

internal fun createAppLaunchIntent(context: Context): Intent {
    val launchIntent = context.packageManager.getLaunchIntentForPackage(context.packageName)
        ?: Intent(context, MainActivity::class.java)
    return launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
}
