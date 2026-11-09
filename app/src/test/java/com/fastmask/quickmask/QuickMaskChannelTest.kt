package com.fastmask.quickmask

import org.junit.Assert.assertFalse
import org.junit.Test

/**
 * `createNotificationChannel` is a no-op for an id the system has seen, so a changed channel
 * field only reaches a device under a new id and the superseded ids are deleted. The one
 * invariant worth a test is that this clean-up never deletes the channel in use.
 */
class QuickMaskChannelTest {

    @Test
    fun `the current channel is never in the stale list`() {
        // staleIds feeds deleteNotificationChannel. Deleting the channel we just created removes
        // the user's own importance/sound choices with it.
        assertFalse(
            "staleIds would delete the channel in use (${QuickMaskChannel.id})",
            QuickMaskChannel.id in QuickMaskChannel.staleIds,
        )
    }
}
