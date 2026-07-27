package com.fastmask.quickmask

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * The notification channel, and the trap in `createNotificationChannel`.
 *
 * `QuickMaskNotifier.ensureChannel()` builds a channel and calls
 * `createNotificationChannel` on every notification. For an id the system has
 * seen before, that call is a NO-OP: importance, lock-screen visibility and
 * description are read once, at creation, and never again — the user owns them
 * afterwards. Today that is harmless, because quick-create is unreleased and
 * nobody has the old channel. It stops being harmless the first time a field
 * changes: the source will say VISIBILITY_SECRET while devices that ran the
 * previous build keep whatever the previous build asked for, and the mismatch
 * is invisible in code review.
 *
 * ## What this file assumes
 *
 * The tests below are split deliberately.
 *
 * - [the channel update strategy is documented in the notifier] and
 *   [the notifier creates and posts under one id] hold under EVERY resolution,
 *   including "leave the call as it is and write down why". They only demand
 *   that the decision is written where the next person will read it.
 *
 * - The rest specify the versioned-id scheme: a new id when the channel
 *   contract changes, with the superseded ids deleted so the user is not left
 *   with a dead duplicate in system settings. If the implementer resolves P3
 *   another way, they own deleting this section together with
 *   `QuickMaskChannel` — but not the documentation tests, and not silently.
 */
class QuickMaskChannelTest {

    private val notifierSource =
        File("src/main/java/com/fastmask/quickmask/QuickMaskNotifier.kt").readText()

    // --- true under any resolution -------------------------------------------

    @Test
    fun `the channel update strategy is documented in the notifier`() {
        // The one thing that must not survive this change is a reader assuming
        // the code they see is what the device does.
        val mentionsTheTrap = Regex("""no-?op""", RegexOption.IGNORE_CASE)
            .containsMatchIn(notifierSource)
        val mentionsTheRemedy = listOf("deleteNotificationChannel", "QuickMaskChannel", "bump")
            .any { notifierSource.contains(it) }

        assertTrue(
            "QuickMaskNotifier does not explain what happens to the channel on an update. " +
                "createNotificationChannel is ignored for an existing id, so a changed " +
                "importance or lockscreenVisibility never reaches a device that ran an " +
                "older build. Say so in a comment, and say what is done about it " +
                "(a versioned id + deleteNotificationChannel, an explicit recreate, or a " +
                "documented decision to bump the id by hand next time).",
            mentionsTheTrap && mentionsTheRemedy,
        )
    }

    @Test
    fun `the notifier creates and posts under one id`() {
        // Two literals drifting apart posts notifications into a channel that
        // was never created — on API 26+ the post is dropped outright.
        assertFalse(
            "QuickMaskNotifier hard-codes a channel id string; it must reference the " +
                "single constant so creation and posting cannot disagree",
            Regex(""""quick_mask""").containsMatchIn(notifierSource),
        )
    }

    // --- the versioned-id scheme ---------------------------------------------

    @Test
    fun `the version is a positive number`() {
        assertTrue("channel version must start at 1", QuickMaskChannel.VERSION >= 1)
    }

    @Test
    fun `the id in use is the id of the current version`() {
        assertEquals(QuickMaskChannel.idFor(QuickMaskChannel.VERSION), QuickMaskChannel.id)
    }

    @Test
    fun `every version gets its own id`() {
        // The whole mechanism: a bumped version has to be a channel the system
        // has never seen, otherwise the new fields are ignored exactly as
        // before.
        val ids = (1..5).map { QuickMaskChannel.idFor(it) }
        assertEquals("versions share an id: $ids", ids.size, ids.toSet().size)
        assertNotEquals(QuickMaskChannel.idFor(1), QuickMaskChannel.idFor(2))
    }

    @Test
    fun `the id is stable for a given version`() {
        // Ids are persisted by the system and carried by every posted
        // notification; a value derived from anything mutable would orphan them.
        repeat(3) {
            assertEquals(QuickMaskChannel.idFor(3), QuickMaskChannel.idFor(3))
        }
    }

    @Test
    fun `the current channel is never in the stale list`() {
        // staleIds feeds deleteNotificationChannel. Deleting the channel we
        // just created removes the user's own importance/sound choices with it.
        val stale = QuickMaskChannel.staleIds(QuickMaskChannel.VERSION)
        assertFalse(
            "staleIds would delete the channel in use (${QuickMaskChannel.id}): $stale",
            QuickMaskChannel.id in stale,
        )
    }

    @Test
    fun `every superseded version is deleted`() {
        val version = 4
        val stale = QuickMaskChannel.staleIds(version)
        (1 until version).forEach { old ->
            assertTrue(
                "v$old channel (${QuickMaskChannel.idFor(old)}) survives a bump to v$version: $stale",
                QuickMaskChannel.idFor(old) in stale,
            )
        }
        assertFalse(QuickMaskChannel.idFor(version) in stale)
    }

    @Test
    fun `the original unversioned channel is deleted too`() {
        // The id that shipped before versioning existed. It is not idFor(n) for
        // any n, so nothing else in the scheme would ever clean it up, and a
        // user who ran a build carrying it would keep an orphan channel in
        // system settings forever.
        val stale = QuickMaskChannel.staleIds(QuickMaskChannel.VERSION)
        if (QuickMaskChannel.id != QUICK_MASK_CHANNEL_ID) {
            assertTrue(
                "the legacy channel id \"$QUICK_MASK_CHANNEL_ID\" is never deleted: $stale",
                QUICK_MASK_CHANNEL_ID in stale,
            )
        }
    }

    @Test
    fun `the stale list has no duplicates`() {
        val stale = QuickMaskChannel.staleIds(7)
        assertEquals("duplicate ids in $stale", stale.size, stale.toSet().size)
    }

    @Test
    fun `version one has nothing to clean up beyond the legacy id`() {
        // A fresh scheme must not invent ids to delete; deleting an id the
        // system does not know is harmless but noisy, and it hides real
        // deletions in a log.
        val stale = QuickMaskChannel.staleIds(1)
        assertTrue(
            "v1 wants to delete channels that never existed: $stale",
            stale.all { it == QUICK_MASK_CHANNEL_ID },
        )
    }
}
