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
 * Every notification slot quick-create posts in, with the PendingIntent request
 * codes that belong to it. The single source for both namespaces.
 *
 * Slots are separate because `notify` replaces whatever already sits under an
 * id: a failed create must not wipe the "Mask created" notification (and its
 * Undo, the only way back from a mask created by accident), nor a success erase
 * an error the user has not read yet.
 *
 * Request codes are separate because FLAG_UPDATE_CURRENT rewrites the
 * PendingIntent registered under a (requestCode, intent) pair; two live
 * notifications sharing a code would overwrite each other's target.
 *
 * A slot that has an intent has its request code as a plain `Int`: the slots
 * that carry an "open the app" intent are [OpensApp], the one with an Undo is
 * [HasUndo], so asking a slot for an intent it does not have does not compile.
 */
internal sealed interface QuickMaskSlot {
    val notificationId: Int

    /** A slot whose notification opens the app when tapped. */
    sealed interface OpensApp : QuickMaskSlot {
        val openRequestCode: Int
    }

    /** A slot whose notification carries an "Undo" action. */
    sealed interface HasUndo : QuickMaskSlot {
        val undoRequestCode: Int
    }

    /** The only slot carrying an Undo; cancelled the moment Undo is tapped. */
    data object CREATED : OpensApp, HasUndo {
        override val notificationId = 7_301
        override val openRequestCode = 7_302
        override val undoRequestCode = 7_303
    }

    data object FAILURE : QuickMaskSlot {
        override val notificationId = 7_304
    }

    /**
     * Result of "Undo". Its own id, because reusing [CREATED] would resurrect a
     * notification the user just dismissed, and [FAILURE] may carry an error
     * they have not read yet.
     */
    data object UNDO_RESULT : OpensApp {
        override val notificationId = 7_305
        override val openRequestCode = 7_306
    }

    companion object {
        val all: List<QuickMaskSlot> = listOf(CREATED, FAILURE, UNDO_RESULT)
    }
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
internal fun createAppPendingIntent(context: Context, slot: QuickMaskSlot.OpensApp): PendingIntent =
    PendingIntent.getActivity(
        context,
        slot.openRequestCode,
        createAppLaunchIntent(context),
        QUICK_MASK_PENDING_FLAGS,
    )

/** The "Undo" action's broadcast PendingIntent for the mask [maskId]. */
internal fun createUndoPendingIntent(context: Context, slot: QuickMaskSlot.HasUndo, maskId: String): PendingIntent =
    PendingIntent.getBroadcast(
        context,
        slot.undoRequestCode,
        Intent(context, QuickMaskUndoReceiver::class.java).putExtra(EXTRA_QUICK_MASK_ID, maskId),
        QUICK_MASK_PENDING_FLAGS,
    )
