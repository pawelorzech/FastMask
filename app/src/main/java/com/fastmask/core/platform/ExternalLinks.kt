package com.fastmask.core.platform

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent

/**
 * Catches [ActivityNotFoundException] instead of guarding with `resolveActivity`:
 * on Android 11+ package-visibility filtering makes that answer null for working
 * `mailto:` / `https:` handlers, and a silent no-op is never acceptable on a
 * support or legal link.
 */
fun openExternalIntent(context: Context, intent: Intent): Boolean =
    try {
        context.startActivity(intent)
        true
    } catch (e: ActivityNotFoundException) {
        false
    }
