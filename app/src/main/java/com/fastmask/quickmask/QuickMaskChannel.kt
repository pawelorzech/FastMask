package com.fastmask.quickmask

/**
 * `NotificationManager.createNotificationChannel` is a no-op for an id that
 * already exists: importance, lock-screen visibility and description are read
 * once, at creation, and every later call is ignored. So a channel field
 * changed in source silently does not change on any device that already ran an
 * older build.
 */
internal object QuickMaskChannel {

    /** Bumped whenever a field of the channel changes. */
    const val VERSION: Int = 2

    /**
     * The id the channel had before versioning existed. Every versioned id is
     * built on it, and it is itself deleted as stale (see [staleIds]).
     */
    const val LEGACY_ID: String = "quick_mask"

    /** The id the notifier posts under today. */
    val id: String = idFor(VERSION)

    /**
     * Ids of superseded channels that should be removed from system settings:
     * the id that shipped before versioning existed (it is `idFor(n)` for no n,
     * so nothing else in the scheme would ever clean it up), then every earlier
     * version.
     *
     * This list never contains the current id: deleting the live channel would
     * also delete the user's own importance and sound choices. Bumping
     * [VERSION] is the deliberate action that ships a channel FIELD change,
     * because the system only re-reads those fields for an id it has not seen.
     */
    val staleIds: List<String> = listOf(LEGACY_ID) + (1 until VERSION).map(::idFor)

    private fun idFor(version: Int): String = "${LEGACY_ID}_v$version"
}
