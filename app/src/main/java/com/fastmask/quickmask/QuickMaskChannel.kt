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
    const val VERSION: Int = 1

    /**
     * `NotificationManager.IMPORTANCE_HIGH`, spelled out so this file stays
     * Android-free. The value is public platform API and cannot change.
     *
     * Importance and lock-screen visibility are INDEPENDENT fields: a heads-up
     * banner is what the user sees while looking at their unlocked phone, and
     * `VISIBILITY_SECRET` is what keeps the same notification off the lock
     * screen. Raising one does not weaken the other.
     */
    const val IMPORTANCE_HIGH: Int = 4

    /** Importance the channel is created with. */
    const val IMPORTANCE: Int = 3

    /** The id the notifier posts under today. */
    val id: String = idFor(VERSION)

    fun idFor(version: Int): String = "${QUICK_MASK_CHANNEL_ID}_v$version"

    /**
     * Ids of superseded channels that should be removed from system settings.
     *
     * This list never contains the current id: deleting the live channel would
     * also delete the user's own importance and sound choices. Bumping
     * [VERSION] is the deliberate action that ships a channel FIELD change,
     * because the system only re-reads those fields for an id it has not seen.
     */
    fun staleIds(currentVersion: Int): List<String> = buildList {
        // The id that shipped before versioning existed; it is idFor(n) for no
        // n, so nothing else in the scheme would ever clean it up.
        if (QUICK_MASK_CHANNEL_ID != idFor(currentVersion)) add(QUICK_MASK_CHANNEL_ID)
        for (version in 1 until currentVersion) add(idFor(version))
    }
}
