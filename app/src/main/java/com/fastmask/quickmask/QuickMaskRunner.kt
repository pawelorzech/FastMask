package com.fastmask.quickmask

import android.content.Context
import android.util.Log
import com.fastmask.R
import com.fastmask.domain.usecase.QuickMaskCreator
import com.fastmask.domain.usecase.QuickMaskResult
import com.fastmask.ui.common.UiErrors
import com.fastmask.ui.common.copyToClipboard
import com.fastmask.ui.common.openExternalIntent
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Singleton
class QuickMaskRunner @Inject constructor(
    @ApplicationContext private val context: Context,
    private val quickMaskCreator: QuickMaskCreator,
    private val notifier: QuickMaskNotifier,
) {

    /**
     * Last line of defence for this scope.
     *
     * Without a handler, a throwable escaping one of these coroutines reaches
     * the default handler and takes the PROCESS down — triggered from a tile
     * tap or a notification button, i.e. from outside the app's own UI, where
     * the user has no idea what crashed. The realistic sources are the hand-off
     * to the app (`startActivityAndCollapse` throws once the tile has left its
     * click window; a background activity launch can be refused) and whatever
     * the storage layer throws before `create()`'s own runCatching is reached.
     */
    private val crashGuard = CoroutineExceptionHandler { _, throwable ->
        Log.w(TAG, "quick mask work failed", throwable)
        createInFlight.set(false)
        notifyFailure(throwable)
    }

    // The shade can tear down TileService as soon as it collapses; this scope
    // belongs to the singleton runner so create/undo work survives that.
    private val scope: CoroutineScope =
        CoroutineScope(SupervisorJob() + Dispatchers.IO + crashGuard)

    /**
     * True from the moment a create is accepted until it settles.
     *
     * Every ViewModel that can mint a mask sets an in-flight flag synchronously
     * before launching, "against rapid double-tap… two taps in one frame would
     * otherwise both land here and create two real masks on the Fastmail
     * account" (CreateMaskedEmailViewModel). This entry point had no such
     * guard, and it is the easiest one to hit twice: a Quick Settings tile
     * gives no feedback until the notification arrives a network round-trip
     * later, so three impatient taps meant three real masks — with one
     * notification, because they share a notification id, so only the last was
     * undoable and only the last reached the clipboard.
     *
     * compareAndSet, not a plain Boolean: tile clicks and notification actions
     * arrive on the main thread, but the release happens on Dispatchers.IO.
     */
    private val createInFlight = AtomicBoolean(false)

    fun launchCreate(openApp: (() -> Unit)? = null) {
        if (!createInFlight.compareAndSet(false, true)) {
            return
        }
        scope.launch {
            // A throwable escaping the gates (storage) is the same outcome as an
            // API rejection: no mask, and the user gets told why.
            val result = runCatching { quickMaskCreator.create() }
                .getOrElse { throwable -> QuickMaskResult.Failed(cause = throwable) }

            when (result) {
                is QuickMaskResult.Created -> {
                    copyToClipboard(context, result.email)
                    notifier.showCreated(id = result.id)
                }
                // Nothing was created on any of these. Signing in, leaving demo
                // mode and passing the biometric gate all happen in the app —
                // the tile lives above the lock screen and must not be a way
                // around it.
                QuickMaskResult.NotSignedIn,
                QuickMaskResult.DemoMode,
                QuickMaskResult.LockRequired -> handOffToApp(openApp)
                // The domain layer reports the cause; mapping it to a localized
                // message happens here, in the Android layer, and nowhere else.
                is QuickMaskResult.Failed -> notifyFailure(result.cause)
            }
            // In the coroutine's own body rather than a finally: a throwable
            // escaping here is caught by crashGuard, which releases the flag
            // too. Both paths must release, or the tile is dead for the rest
            // of the process.
            createInFlight.set(false)
        }
    }

    /**
     * @param onFinished released once the delete has been attempted. The
     *   notification's Undo button hands us a `BroadcastReceiver.goAsync`
     *   result through this callback: until it is finished the process keeps a
     *   live component, so the delete is not racing the low-memory killer.
     */
    fun launchUndo(id: String, onFinished: () -> Unit = {}) {
        scope.launch {
            try {
                // Dismiss FIRST. The tap is the user's decision; leaving the
                // notification up until the network round-trip returns (or
                // forever, when it fails) reads as "the button did nothing".
                // The "mask created" slot is the only one carrying an Undo.
                notifier.cancel(QUICK_MASK_CREATED_NOTIFICATION_ID)
                val success = runCatching { quickMaskCreator.undo(id) }.getOrDefault(false)
                notifier.showUndoResult(success = success)
            } finally {
                onFinished()
            }
        }
    }

    /**
     * Hands off to the app, guarded on every path.
     *
     * [openApp] is the tile's own collapse-the-shade hop; the default is the
     * shortcut/receiver path, which goes through [openExternalIntent] so the
     * codebase keeps exactly one guarded `startActivity`. A hand-off that fails
     * says so instead of disappearing — the user tapped something.
     */
    private suspend fun handOffToApp(openApp: (() -> Unit)?) {
        withContext(Dispatchers.Main) {
            val opened = runCatching {
                if (openApp != null) {
                    openApp()
                    true
                } else {
                    openExternalIntent(context, createAppLaunchIntent(context))
                }
            }.getOrElse { throwable ->
                Log.w(TAG, "could not open the app", throwable)
                false
            }
            if (!opened) {
                notifier.showFailure(messageRes = R.string.error_generic)
            }
        }
    }

    /** The one place a quick-create throwable becomes a localized message. */
    private fun notifyFailure(throwable: Throwable?) {
        notifier.showFailure(UiErrors.messageRes(throwable, R.string.create_email_error_failed))
    }

    private companion object {
        const val TAG = "QuickMask"
    }
}
