package com.fastmask.quickmask

/**
 * `NotificationManager.createNotificationChannel` is a no-op for an id that
 * already exists: importance, lock-screen visibility and description are read
 * once, at creation, and every later call is ignored. So a channel field
 * changed in source silently does not change on any device that already ran an
 * older build.
 */
internal object QuickMaskChannel {

    const val VERSION: Int = 2

    const val LEGACY_ID: String = "quick_mask"

    val id: String = idFor(VERSION)

    // Never contains the current id: deleting the live channel would also delete
    // the user's own importance and sound choices.
    val staleIds: List<String> = listOf(LEGACY_ID) + (1 until VERSION).map(::idFor)

    private fun idFor(version: Int): String = "${LEGACY_ID}_v$version"
}
