package com.fastmask.ui.common

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent

/**
 * Starts an intent aimed at another app, reporting whether it actually opened.
 *
 * Replaces the `resolveActivity(packageManager) != null` guard that used to
 * wrap every external link. That guard was wrong twice over:
 *
 * 1. On Android 11+ `resolveActivity` is subject to package-visibility
 *    filtering, so it answers null for `mailto:` / `https:` unless the app
 *    declares a matching `<queries>` entry — which turned working links into
 *    dead taps. The manifest now declares them, but the check is still not the
 *    right contract: visibility can also be denied for other reasons.
 * 2. A null result led to an empty `if` body, i.e. a tap that did nothing at
 *    all. Silent failure is never acceptable on a support or legal link.
 *
 * Launching and catching [ActivityNotFoundException] is the behaviour Android
 * actually guarantees, and the boolean lets callers surface a message.
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
