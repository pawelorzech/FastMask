package com.fastmask.quickmask

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import com.fastmask.MainActivity

/**
 * The action carried by the launcher shortcut (see `res/xml/shortcuts.xml`).
 *
 * QuickMaskActivity is not exported, so nothing outside this package can start
 * it at all; this action is the second lock on the same door. Any launch that
 * does not carry it is treated as "just open the app", never as consent to
 * create a mask on the user's Fastmail account.
 */
internal const val ACTION_QUICK_MASK: String = "com.fastmask.action.QUICK_MASK"

/**
 * Legacy, pre-versioning notification channel id.
 *
 * New notifications do not post under this id anymore; it stays so
 * [QuickMaskChannel.staleIds] can remove the channel from devices that already
 * created it before versioned ids existed. See [QuickMaskChannel] for the id
 * the notifier uses now.
 */
internal const val QUICK_MASK_CHANNEL_ID: String = "quick_mask"

/**
 * Every notification slot quick-create posts in, with the PendingIntent request
 * codes that belong to it. The single source for both namespaces.
 *
 * Slots are separate because `notify` replaces whatever already sits under an
 * id. Success and failure once shared one, so a failed create wiped the "Mask
 * created" notification — and the Undo action on it, the only way back from a
 * mask created by accident — while a successful one silently erased an error
 * the user had not read yet.
 *
 * Request codes are separate because FLAG_UPDATE_CURRENT rewrites the
 * PendingIntent registered under a (requestCode, intent) pair; two live
 * notifications sharing a code would overwrite each other's target.
 */
internal enum class QuickMaskSlot(
    val notificationId: Int,
    /** "Open the app" intent of this slot's notification, or null when it has none. */
    val openRequestCode: Int? = null,
    /** "Undo" action of this slot's notification, or null when it has none. */
    val undoRequestCode: Int? = null,
) {
    /** The only slot carrying an Undo; cancelled the moment Undo is tapped. */
    CREATED(notificationId = 7_301, openRequestCode = 7_302, undoRequestCode = 7_303),

    FAILURE(notificationId = 7_304),

    /**
     * Result of "Undo". Its own id, because reusing [CREATED] would resurrect a
     * notification the user just dismissed, and [FAILURE] may carry an error
     * they have not read yet.
     */
    UNDO_RESULT(notificationId = 7_305, openRequestCode = 7_306),
}

/** Flags for every PendingIntent here: immutable, so the system cannot rewrite where it goes. */
internal const val QUICK_MASK_PENDING_FLAGS: Int =
    PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT

internal const val EXTRA_QUICK_MASK_ID: String = "quick_mask_id"

internal fun createAppLaunchIntent(context: Context): Intent {
    val launchIntent = context.packageManager.getLaunchIntentForPackage(context.packageName)
        ?: Intent(context, MainActivity::class.java)
    return launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
}

/** The "open FastMask" PendingIntent of [slot]'s notification (the tile reuses [QuickMaskSlot.CREATED]'s). */
internal fun createAppPendingIntent(context: Context, slot: QuickMaskSlot): PendingIntent =
    PendingIntent.getActivity(
        context,
        checkNotNull(slot.openRequestCode) { "$slot has no open intent" },
        createAppLaunchIntent(context),
        QUICK_MASK_PENDING_FLAGS,
    )

/** The "Undo" action's broadcast PendingIntent for the mask [maskId]. */
internal fun createUndoPendingIntent(context: Context, slot: QuickMaskSlot, maskId: String): PendingIntent =
    PendingIntent.getBroadcast(
        context,
        checkNotNull(slot.undoRequestCode) { "$slot has no undo action" },
        Intent(context, QuickMaskUndoReceiver::class.java).putExtra(EXTRA_QUICK_MASK_ID, maskId),
        QUICK_MASK_PENDING_FLAGS,
    )
