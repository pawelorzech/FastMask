package com.fastmask.quickmask

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * P3, second half: the result of "Undo" that nobody sees.
 *
 * `showUndoResult` posts a Toast. The user taps Undo INSIDE the notification
 * shade, and a Toast renders underneath the shade's blur — the reporter's crop
 * shows a shapeless white smear where the words should be. So the confirmation
 * is illegible, and the failure message — *"Could not delete the mask. Open
 * FastMask and try again."* — travels the same dead channel. A failed delete
 * therefore reads exactly like a successful one: the notification disappears,
 * nothing legible replaces it, and the user walks away certain the mask is
 * gone while it is still on their Fastmail account.
 *
 * The result becomes a notification for BOTH outcomes. The Toast survives only
 * where notifications are not permitted — there it is the only channel left,
 * which makes it a deliberate exception rather than the rule.
 */
class QuickMaskUndoPolicyTest {

    private val notifierSource =
        File("src/main/java/com/fastmask/quickmask/QuickMaskNotifier.kt").readText()

    // --- the decision --------------------------------------------------------

    @Test
    fun `a successful undo is confirmed by a notification`() {
        val feedback = QuickMaskUndoPolicy.feedback(success = true, canPostNotification = true)

        assertEquals(UndoFeedbackChannel.NOTIFICATION, feedback.channel)
        assertEquals(UndoFeedbackMessage.UNDONE, feedback.message)
    }

    @Test
    fun `a failed undo is reported by a notification`() {
        // The case that matters most: the mask still exists. A message the user
        // cannot read here is worse than no message, because the disappearing
        // "Mask created" notification already told them it worked.
        val feedback = QuickMaskUndoPolicy.feedback(success = false, canPostNotification = true)

        assertEquals(UndoFeedbackChannel.NOTIFICATION, feedback.channel)
        assertEquals(UndoFeedbackMessage.UNDO_FAILED, feedback.message)
    }

    @Test
    fun `both outcomes are told apart`() {
        val ok = QuickMaskUndoPolicy.feedback(success = true, canPostNotification = true)
        val failed = QuickMaskUndoPolicy.feedback(success = false, canPostNotification = true)

        assertNotEquals(
            "success and failure must not produce the same message — that is the bug",
            ok.message,
            failed.message,
        )
    }

    // --- the fallback --------------------------------------------------------

    @Test
    fun `without notification permission the result falls back to a Toast`() {
        // A denied POST_NOTIFICATIONS leaves no other channel. The Toast is bad;
        // silence is worse.
        val ok = QuickMaskUndoPolicy.feedback(success = true, canPostNotification = false)
        val failed = QuickMaskUndoPolicy.feedback(success = false, canPostNotification = false)

        assertEquals(UndoFeedbackChannel.TOAST, ok.channel)
        assertEquals(UndoFeedbackChannel.TOAST, failed.channel)
        assertEquals(UndoFeedbackMessage.UNDONE, ok.message)
        assertEquals(UndoFeedbackMessage.UNDO_FAILED, failed.message)
    }

    @Test
    fun `the Toast fallback carries no notification slot`() {
        val feedback = QuickMaskUndoPolicy.feedback(success = false, canPostNotification = false)

        assertNull(
            "a Toast has no notification id; a non-null one invites a notify() call the " +
                "platform would reject",
            feedback.notificationId,
        )
    }

    @Test
    fun `the result is never dropped`() {
        // Four combinations, four messages. No silent branch.
        listOf(true, false).forEach { success ->
            listOf(true, false).forEach { canPost ->
                val feedback = QuickMaskUndoPolicy.feedback(success, canPost)
                assertTrue(
                    "undo(success=$success, canPost=$canPost) produced no message",
                    feedback.message in UndoFeedbackMessage.values(),
                )
            }
        }
    }

    // --- which slot ----------------------------------------------------------

    @Test
    fun `the undo result posts in its own slot`() {
        val ok = QuickMaskUndoPolicy.feedback(success = true, canPostNotification = true)
        val failed = QuickMaskUndoPolicy.feedback(success = false, canPostNotification = true)

        assertEquals(QUICK_MASK_UNDO_NOTIFICATION_ID, ok.notificationId)
        assertEquals(QUICK_MASK_UNDO_NOTIFICATION_ID, failed.notificationId)
    }

    @Test
    fun `the undo slot collides with neither quick-create slot`() {
        // The "mask created" notification is cancelled the moment Undo is
        // tapped — reusing its id would resurrect a notification the user just
        // dismissed, Undo button and all. The quick-create failure slot may be
        // carrying an unrelated error the user has not read.
        val ids = listOf(
            QUICK_MASK_CREATED_NOTIFICATION_ID,
            QUICK_MASK_FAILURE_NOTIFICATION_ID,
            QUICK_MASK_UNDO_NOTIFICATION_ID,
        )

        assertEquals("quick-mask notification ids collide: $ids", ids.size, ids.toSet().size)
    }

    // --- wiring --------------------------------------------------------------

    @Test
    fun `the notifier routes the undo result through the policy`() {
        assertTrue(
            "QuickMaskNotifier.showUndoResult still decides for itself; the choice between " +
                "notification and Toast belongs in QuickMaskUndoPolicy where it is tested",
            notifierSource.contains("QuickMaskUndoPolicy"),
        )
    }

    @Test
    fun `showUndoResult no longer goes straight to a Toast`() {
        val body = Regex("""fun showUndoResult\([\s\S]*?\n    \}""").find(notifierSource)?.value

        assertTrue("showUndoResult is gone from QuickMaskNotifier", body != null)
        assertTrue(
            "showUndoResult still calls showToast unconditionally — the Toast is the " +
                "no-permission fallback, not the default path:\n$body",
            body!!.contains("QuickMaskUndoPolicy") || body.contains("postOrToast"),
        )
    }

    @Test
    fun `the undo result carries no masked address`() {
        // Same privacy rule as the creation notification, and for the same
        // reason: a heads-up banner is drawn over whatever is on screen. The
        // signature is the enforcement — there is no address to leak if it is
        // never passed in.
        assertTrue(
            "showUndoResult must keep taking only the outcome",
            Regex("""fun showUndoResult\(success: Boolean\)""").containsMatchIn(notifierSource),
        )
    }
}
