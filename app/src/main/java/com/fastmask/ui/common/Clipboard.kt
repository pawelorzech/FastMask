package com.fastmask.ui.common

import android.content.ClipData
import android.content.ClipDescription
import android.content.ClipboardManager
import android.content.Context
import android.os.Build
import android.os.PersistableBundle
import com.fastmask.R

/**
 * Copies [value] to the system clipboard under a neutral label. Shared by the
 * list card quick-copy and the detail screen so the behaviour stays identical.
 *
 * The clip is flagged sensitive on Android 13+: a masked address is exactly
 * what this app exists to protect, so it must not show up in plaintext in the
 * system clipboard-preview overlay or be treated casually by clipboard history.
 *
 * Never throws (some OEM clipboard implementations do), so a failure cannot
 * take down the caller. Callers that tell the user "copied" must check the
 * result: it is true only when the clip was actually set.
 */
fun copyToClipboard(context: Context, value: String): Boolean = runCatching {
    val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
        ?: return@runCatching false
    val clip = ClipData.newPlainText(context.getString(R.string.app_name), value)
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        clip.description.extras = PersistableBundle().apply {
            putBoolean(ClipDescription.EXTRA_IS_SENSITIVE, true)
        }
    }
    cm.setPrimaryClip(clip)
    true
}.getOrDefault(false)
