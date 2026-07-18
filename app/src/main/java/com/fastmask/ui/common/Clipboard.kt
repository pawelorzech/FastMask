package com.fastmask.ui.common

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context

/**
 * Copies [value] to the system clipboard under a neutral label. Shared by the
 * list card quick-copy and the detail screen so the behaviour stays identical.
 */
fun copyToClipboard(context: Context, value: String) {
    val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    cm.setPrimaryClip(ClipData.newPlainText("Masked email", value))
}
