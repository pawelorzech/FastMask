package com.fastmask.core.platform

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent

/**
 * Starts an intent aimed at another app, reporting whether it actually opened.
 *
 * Launches and catches [ActivityNotFoundException] instead of guarding with
 * `resolveActivity`: on Android 11+ package-visibility filtering makes that
 * answer null for working `mailto:` / `https:` handlers, and a silent no-op is
 * never acceptable on a support or legal link.
 *
 * @return true when a handler took the intent, false when none exists.
 */
fun openExternalIntent(context: Context, intent: Intent): Boolean =
    try {
        context.startActivity(intent)
        true
    } catch (e: ActivityNotFoundException) {
        false
    }
