package com.fastmask.quickmask

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The notification channel, and the trap in `createNotificationChannel`.
 *
 * For an id the system has seen before, `createNotificationChannel` is a NO-OP: importance,
 * lock-screen visibility and description are read once, at creation, and never again. A changed
 * channel field therefore only reaches a device under a NEW id, and the superseded ids must be
 * deleted so the user is not left with a dead duplicate in system settings. These tests hold the
 * invariants of that versioned-id scheme; they deliberately do not re-derive the id format.
 */
class QuickMaskChannelTest {

    @Test
    fun `the channel asks for a heads-up importance`() {
        // IMPORTANCE_HIGH == 4. At IMPORTANCE_DEFAULT (3) the "Mask created" notification, and the
        // Undo action that is the only way back from a mistaken mask, never becomes visible.
        assertEquals(4, QuickMaskChannel.IMPORTANCE)
    }

    @Test
    fun `raising the importance shipped under a new channel id`() {
        // The system re-reads importance only for an id it has never seen.
        assertTrue(QuickMaskChannel.VERSION >= 2)
        assertTrue("quick_mask_v1" in QuickMaskChannel.staleIds)
    }

    @Test
    fun `the current channel is never in the stale list`() {
        // staleIds feeds deleteNotificationChannel. Deleting the channel we just created removes
        // the user's own importance/sound choices with it.
        assertFalse(
            "staleIds would delete the channel in use (${QuickMaskChannel.id})",
            QuickMaskChannel.id in QuickMaskChannel.staleIds,
        )
    }

    @Test
    fun `every superseded version is deleted`() {
        val stale = QuickMaskChannel.staleIds
        (1 until QuickMaskChannel.VERSION).forEach { old ->
            assertTrue(
                "v$old channel survives the bump to v${QuickMaskChannel.VERSION}: $stale",
                "${QUICK_MASK_CHANNEL_ID}_v$old" in stale,
            )
        }
    }

    @Test
    fun `the original unversioned channel is deleted too`() {
        // It is idFor(n) for no n, so nothing else in the scheme would ever clean it up.
        assertTrue(QUICK_MASK_CHANNEL_ID in QuickMaskChannel.staleIds)
    }

    @Test
    fun `the stale list has no duplicates`() {
        val stale = QuickMaskChannel.staleIds
        assertEquals("duplicate ids in $stale", stale.size, stale.toSet().size)
    }
}
