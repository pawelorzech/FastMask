package com.fastmask.quickmask

/**
 * STUB — written by the test author, to be implemented (or deliberately
 * rejected; see QuickMaskChannelTest).
 *
 * `NotificationManager.createNotificationChannel` is a no-op for an id that
 * already exists: importance, lock-screen visibility and description are read
 * once, at creation, and every later call is ignored. So a channel field
 * changed in source silently does not change on any device that already ran an
 * older build.
 */
internal object QuickMaskChannel {

    /** Bumped whenever a field of the channel changes. */
    const val VERSION: Int = 1

    /** The id the notifier posts under today. */
    val id: String get() = QUICK_MASK_CHANNEL_ID

    /** STUB. */
    @Suppress("UNUSED_PARAMETER")
    fun idFor(version: Int): String = QUICK_MASK_CHANNEL_ID

    /** STUB: ids of superseded channels that must be deleted. */
    @Suppress("UNUSED_PARAMETER")
    fun staleIds(currentVersion: Int): List<String> = emptyList()
}
