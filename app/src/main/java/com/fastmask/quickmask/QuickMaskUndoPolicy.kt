package com.fastmask.quickmask

/** Where the result of the notification's "Undo" action is shown. */
internal enum class UndoFeedbackChannel { NOTIFICATION, TOAST }

/** Which of the two existing strings the result carries. */
internal enum class UndoFeedbackMessage { UNDONE, UNDO_FAILED }

/**
 * @param notificationId the slot to post in, or null on the Toast path.
 */
internal data class UndoFeedback(
    val channel: UndoFeedbackChannel,
    val message: UndoFeedbackMessage,
    val notificationId: Int?,
)

/**
 * What to show after "Undo".
 *
 * The result used to be a Toast unconditionally. A Toast is drawn UNDER the
 * notification shade's blur — and the shade is exactly where the user is
 * standing when they tap Undo — so the one message that matters rendered as an
 * unreadable smear. The failure case is the dangerous half: the same silence
 * leaves the user certain the mask is gone while it still exists on the
 * account.
 *
 * A notification is visible wherever the user is looking, so it is the default
 * for BOTH outcomes. The Toast survives only as the fallback for a user who has
 * denied notifications — there the choice is a bad message or no message.
 */
internal object QuickMaskUndoPolicy {

    /**
     * @param success whether the mask was actually deleted.
     * @param canPostNotification result of [QuickMaskPolicy.canPostNotification].
     */
    fun feedback(success: Boolean, canPostNotification: Boolean): UndoFeedback {
        val message = when (success) {
            true -> UndoFeedbackMessage.UNDONE
            false -> UndoFeedbackMessage.UNDO_FAILED
        }

        return if (canPostNotification) {
            UndoFeedback(
                channel = UndoFeedbackChannel.NOTIFICATION,
                message = message,
                notificationId = QUICK_MASK_UNDO_NOTIFICATION_ID,
            )
        } else {
            UndoFeedback(
                channel = UndoFeedbackChannel.TOAST,
                message = message,
                notificationId = null,
            )
        }
    }
}
