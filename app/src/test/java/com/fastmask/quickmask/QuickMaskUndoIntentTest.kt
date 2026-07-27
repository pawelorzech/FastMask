package com.fastmask.quickmask

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * The undo-result notification used to be a dead end.
 *
 * Its failure text is an instruction — *"Could not delete the mask. Open
 * FastMask and try again."* — and the notification carried no `contentIntent`,
 * so tapping the thing that says "open FastMask" did nothing at all. The user
 * is left holding a mask that still exists on their Fastmail account and a
 * message that cannot be acted on.
 */
class QuickMaskUndoIntentTest {

    private val notifierSource =
        File("src/main/java/com/fastmask/quickmask/QuickMaskNotifier.kt").readText()

    private val undoResultBody: String
        get() = requireNotNull(
            Regex("""fun showUndoResult\([\s\S]*?\n    \}""").find(notifierSource)?.value,
        ) { "showUndoResult is gone from QuickMaskNotifier" }

    @Test
    fun `the undo result notification can be tapped`() {
        assertTrue(
            "showUndoResult builds a notification without setContentIntent — the message " +
                "tells the user to open FastMask and tapping it does nothing:\n$undoResultBody",
            undoResultBody.contains(".setContentIntent("),
        )
    }

    @Test
    fun `tapping it opens the app`() {
        assertTrue(
            "the undo result's contentIntent must launch the app the same way the other " +
                "quick-mask notifications do, so the biometric gate stays in the path:\n" +
                undoResultBody,
            undoResultBody.contains("createAppLaunchIntent(context)"),
        )
    }

    @Test
    fun `the PendingIntent is immutable`() {
        // Same rule as every other PendingIntent in this file: a mutable one
        // handed to SystemUI is an intent any app holding it can rewrite.
        val pendingIntents = Regex("""PendingIntent\.get\w+\(""").findAll(notifierSource).count()
        val immutableFlags = Regex("""PendingIntent\.FLAG_IMMUTABLE""").findAll(notifierSource).count()

        assertEquals(
            "every PendingIntent in QuickMaskNotifier must be FLAG_IMMUTABLE",
            pendingIntents,
            immutableFlags,
        )
        assertTrue(
            "the undo result's own PendingIntent is not immutable:\n$undoResultBody",
            undoResultBody.contains("PendingIntent.FLAG_IMMUTABLE"),
        )
    }

    @Test
    fun `the undo result uses a request code of its own`() {
        // FLAG_UPDATE_CURRENT rewrites the PendingIntent registered under a
        // (requestCode, intent) pair. Sharing QUICK_MASK_OPEN_REQUEST_CODE would
        // let the undo notification and the still-visible "mask created" one
        // overwrite each other's slot.
        val codes = listOf(
            QUICK_MASK_OPEN_REQUEST_CODE,
            QUICK_MASK_UNDO_REQUEST_CODE,
            QUICK_MASK_UNDO_OPEN_REQUEST_CODE,
        )

        assertEquals("quick-mask request codes collide: $codes", codes.size, codes.toSet().size)
        assertTrue(
            "the undo result must use QUICK_MASK_UNDO_OPEN_REQUEST_CODE:\n$undoResultBody",
            undoResultBody.contains("QUICK_MASK_UNDO_OPEN_REQUEST_CODE"),
        )
    }

    @Test
    fun `request codes do not collide with notification ids`() {
        // They live in different namespaces, but they are allocated from one
        // block of numbers here; a duplicate would read as a copy-paste slip.
        val all = listOf(
            QUICK_MASK_CREATED_NOTIFICATION_ID,
            QUICK_MASK_OPEN_REQUEST_CODE,
            QUICK_MASK_UNDO_REQUEST_CODE,
            QUICK_MASK_FAILURE_NOTIFICATION_ID,
            QUICK_MASK_UNDO_NOTIFICATION_ID,
            QUICK_MASK_UNDO_OPEN_REQUEST_CODE,
        )

        assertEquals("quick-mask constants collide: $all", all.size, all.toSet().size)
    }

    @Test
    fun `the undo result still keeps the address off the screen`() {
        // Adding a tap target must not turn the notification into a place the
        // masked address could be shown. VISIBILITY_SECRET stays on the builder.
        assertTrue(
            "the undo result notification lost VISIBILITY_SECRET:\n$undoResultBody",
            undoResultBody.contains("NotificationCompat.VISIBILITY_SECRET"),
        )
    }
}
