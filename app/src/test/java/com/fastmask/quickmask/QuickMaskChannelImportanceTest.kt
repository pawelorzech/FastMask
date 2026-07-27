package com.fastmask.quickmask

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * P3, first half: the confirmation the user cannot see.
 *
 * Observed on the reporter's device: after tapping the launcher shortcut the
 * only feedback was a status-bar glyph a few pixels wide. The channel was
 * created with `IMPORTANCE_DEFAULT` (`mImportance=3`), and `IMPORTANCE_DEFAULT`
 * never produces a heads-up banner — so the "Mask created" notification, and
 * with it the Undo button that is the only way back from a mask created by
 * mistake, existed only for someone who thought to pull the shade down.
 *
 * The fix is `IMPORTANCE_HIGH`, and it has two traps around it.
 *
 * 1. **Importance is read once.** `createNotificationChannel` is a no-op for an
 *    id the system already knows, so raising the constant in source changes
 *    nothing on a device that ran an earlier build. The versioned-id scheme in
 *    [QuickMaskChannel] exists for exactly this; a field change that does not
 *    bump [QuickMaskChannel.VERSION] does not ship.
 * 2. **Importance is not visibility.** They are independent fields. The whole
 *    privacy design — no address in the notification, `VISIBILITY_SECRET` on
 *    both the builder and the channel — must survive the raise untouched.
 */
class QuickMaskChannelImportanceTest {

    private val notifierSource =
        File("src/main/java/com/fastmask/quickmask/QuickMaskNotifier.kt").readText()

    /** `NotificationManager.IMPORTANCE_DEFAULT` — no heads-up. */
    private val importanceDefault = 3

    /** `NotificationManager.IMPORTANCE_HIGH` — heads-up banner. */
    private val importanceHigh = 4

    // --- the importance itself -----------------------------------------------

    @Test
    fun `the channel asks for a heads-up importance`() {
        assertEquals(
            "the quick-create confirmation is the only place the Undo action exists; at " +
                "IMPORTANCE_DEFAULT it never becomes visible",
            importanceHigh,
            QuickMaskChannel.IMPORTANCE,
        )
        assertNotEquals(importanceDefault, QuickMaskChannel.IMPORTANCE)
    }

    @Test
    fun `the constant matches the platform value it stands for`() {
        assertEquals(importanceHigh, QuickMaskChannel.IMPORTANCE_HIGH)
    }

    @Test
    fun `the notifier builds the channel from the constant`() {
        // A literal IMPORTANCE_DEFAULT left in the builder makes every test above
        // decorative.
        assertFalse(
            "QuickMaskNotifier still hard-codes NotificationManager.IMPORTANCE_DEFAULT; " +
                "it must create the channel with QuickMaskChannel.IMPORTANCE",
            notifierSource.contains("IMPORTANCE_DEFAULT"),
        )
        assertTrue(
            "QuickMaskNotifier must read the importance from QuickMaskChannel so the value " +
                "and its version live together",
            notifierSource.contains("QuickMaskChannel.IMPORTANCE"),
        )
    }

    // --- shipping the change -------------------------------------------------

    @Test
    fun `changing the importance ships under a new channel id`() {
        // The system re-reads importance only for an id it has never seen.
        assertTrue(
            "QuickMaskChannel.VERSION is still ${QuickMaskChannel.VERSION}. Raising the " +
                "importance without bumping it leaves every existing install on the old, " +
                "silent channel — createNotificationChannel is a no-op for a known id.",
            QuickMaskChannel.VERSION >= 2,
        )
    }

    @Test
    fun `a user already on quick_mask_v1 is moved to the new channel`() {
        val stale = QuickMaskChannel.staleIds(QuickMaskChannel.VERSION)

        assertNotEquals(
            "the live id is still quick_mask_v1 — the raised importance would be ignored",
            "quick_mask_v1",
            QuickMaskChannel.id,
        )
        assertTrue(
            "quick_mask_v1 must be deleted, or the user keeps a dead duplicate in system " +
                "settings next to the new one: $stale",
            "quick_mask_v1" in stale,
        )
    }

    @Test
    fun `the superseded channel is removed before the new one is created`() {
        // Ordering inside ensureChannel: delete the stale ids, then create.
        val deleteAt = notifierSource.indexOf("deleteNotificationChannel")
        val createAt = notifierSource.indexOf("createNotificationChannel(channel)")

        assertTrue("deleteNotificationChannel is gone from the notifier", deleteAt >= 0)
        assertTrue("createNotificationChannel(channel) is gone from the notifier", createAt >= 0)
        assertTrue(
            "stale channels must be deleted before the current one is created",
            deleteAt < createAt,
        )
    }

    @Test
    fun `the legacy unversioned channel is still cleaned up`() {
        val stale = QuickMaskChannel.staleIds(QuickMaskChannel.VERSION)

        assertTrue(
            "the pre-versioning id \"$QUICK_MASK_CHANNEL_ID\" is no longer deleted: $stale",
            QUICK_MASK_CHANNEL_ID in stale,
        )
        assertTrue(
            "every version before the current one must be deleted: $stale",
            (1 until QuickMaskChannel.VERSION).all { QuickMaskChannel.idFor(it) in stale },
        )
        assertFalse("staleIds would delete the live channel: $stale", QuickMaskChannel.id in stale)
    }

    // --- what must NOT change ------------------------------------------------

    @Test
    fun `the raised importance does not reach the lock screen`() {
        // Importance and lock-screen visibility are independent fields. The
        // device evidence for the current build was mLockscreenVisibility=-1000;
        // that must stay true after the raise.
        assertTrue(
            "the channel no longer pins lockscreenVisibility to VISIBILITY_SECRET — a " +
                "heads-up channel without it puts quick-create notifications on the lock screen",
            Regex("""lockscreenVisibility\s*=\s*Notification\.VISIBILITY_SECRET""")
                .containsMatchIn(notifierSource),
        )
        assertTrue(
            "the notification builder no longer sets VISIBILITY_SECRET",
            notifierSource.contains("VISIBILITY_SECRET"),
        )
    }

    @Test
    fun `the masked address stays out of the notification`() {
        // A heads-up banner is drawn over whatever is on screen, which makes the
        // "no address in the text" rule matter MORE, not less.
        val builderBody = Regex("""setContentText\(([^)]*)\)""")
            .findAll(notifierSource)
            .map { it.groupValues[1] }
            .toList()

        assertTrue("no setContentText found in the notifier", builderBody.isNotEmpty())
        builderBody.forEach { argument ->
            assertFalse(
                "the notification body renders something derived from the address: $argument",
                argument.contains("email", ignoreCase = true) || argument.contains("address"),
            )
        }
        assertFalse(
            "showCreated must not take the address at all — it only needs the id for Undo",
            Regex("""fun showCreated\([^)]*email""").containsMatchIn(notifierSource),
        )
    }
}
