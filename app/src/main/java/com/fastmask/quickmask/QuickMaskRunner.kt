package com.fastmask.quickmask

import android.content.Context
import android.util.Log
import com.fastmask.BuildConfig
import com.fastmask.R
import com.fastmask.core.runSuspendCatching
import com.fastmask.ui.common.UiErrors
import com.fastmask.core.platform.copyToClipboard
import com.fastmask.core.platform.openExternalIntent
import com.fastmask.core.IoDispatcher
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout

/**
 * The one shape of "send the user to the app" that both entry points (tile and
 * launcher shortcut) use. [open] returns whether the app was actually opened;
 * a `false` or a throw is reported to the user rather than swallowed.
 */
fun interface AppHandOff {
    fun open(): Boolean
}

@Singleton
class QuickMaskRunner @Inject constructor(
    @ApplicationContext private val context: Context,
    private val quickMaskCreator: QuickMaskCreator,
    private val notifier: QuickMaskNotifier,
    /** Injected so the create and undo guards can be tested deterministically. */
    @IoDispatcher private val dispatcher: CoroutineDispatcher,
) {

    /**
     * Last line of defence: a throwable escaping these coroutines would take the
     * PROCESS down, and they are triggered from a tile tap or notification button,
     * outside the app's own UI. Realistic sources are the hand-off to the app
     * (`startActivityAndCollapse` throws once the tile has left its click window;
     * a background activity launch can be refused) and storage errors thrown
     * before `create()`'s own runCatching.
     */
    private val crashGuard = CoroutineExceptionHandler { _, throwable ->
        Log.w(TAG, "quick mask work failed", throwable)
        notifyFailure(throwable)
    }

    // The shade can tear down TileService as soon as it collapses; this scope
    // belongs to the singleton runner so create/undo work survives that.
    private val scope: CoroutineScope =
        CoroutineScope(SupervisorJob() + dispatcher + crashGuard)

    /**
     * True from the moment a create is accepted until it settles. A Quick Settings
     * tile gives no feedback until the notification arrives, so impatient taps
     * would otherwise create several real masks that share one notification.
     *
     * compareAndSet, not a plain Boolean: tile clicks and notification actions
     * arrive on the main thread, but the release happens on Dispatchers.IO.
     */
    private val createInFlight = AtomicBoolean(false)

    /**
     * Ids whose undo has already been accepted. The notification's Undo button
     * stays on screen for tens of milliseconds after the first tap, so two
     * broadcasts would run `destroy` twice for one id; the second comes back
     * `notFound`, is reported as a failure and overwrites the truthful "Undone".
     *
     * A map rather than a single flag: different masks can be undone in sequence.
     * Bounded (insertion-ordered LRU of [UNDONE_LATCH_LIMIT]) because this
     * singleton lives for the whole process.
     */
    private val undoneLock = Any()
    private val undoneIds = LinkedHashMap<String, Unit>(64)

    /** @return true when THIS call is the one accepted for [id]. */
    private fun latchUndone(id: String): Boolean = synchronized(undoneLock) {
        if (undoneIds.containsKey(id)) return false
        undoneIds[id] = Unit
        if (undoneIds.size > UNDONE_LATCH_LIMIT) {
            val eldest = undoneIds.keys.iterator()
            eldest.next()
            eldest.remove()
        }
        true
    }

    private fun unlatchUndone(id: String) {
        synchronized(undoneLock) { undoneIds.remove(id) }
    }

    /**
     * Quick Settings tile entry. [handOff] is the tile's own collapse-the-shade
     * hop, used when nothing can be created without the app.
     */
    fun createFromTile(handOff: AppHandOff) = launchCreate(handOff)

    /**
     * Launcher-shortcut entry. Hands off through [openExternalIntent], so the
     * codebase keeps exactly one guarded `startActivity`.
     */
    fun createFromShortcut() = launchCreate {
        openExternalIntent(context, createAppLaunchIntent(context))
    }

    private fun launchCreate(handOff: AppHandOff) {
        if (!createInFlight.compareAndSet(false, true)) {
            return
        }
        scope.launch {
            try {
                // A throwable escaping the gates (storage) is the same outcome
                // as an API rejection. Cancellation is different: swallowing it
                // could post a stale failure after Android has cancelled the
                // work, so it must keep its structured-concurrency meaning.
                val result = runSuspendCatching { quickMaskCreator.create() }
                    .getOrElse { throwable -> QuickMaskResult.Failed(cause = throwable) }

                when (result) {
                    is QuickMaskResult.Created -> {
                        // Run clipboard access on Main and guard it: some OEM
                        // ClipboardManager implementations construct a Handler on
                        // the calling thread's Looper, and a clipboard failure
                        // after the mask is already live on Fastmail must never
                        // suppress showCreated() (with its Undo button) or report
                        // a false creation error.
                        val copied = withContext(Dispatchers.Main) {
                            copyToClipboard(context, result.email)
                        }
                        notifier.showCreated(id = result.id, copied = copied)
                    }
                    // Nothing was created on any of these. Signing in, leaving
                    // demo mode and passing the biometric gate all happen in
                    // the app — the tile must not be a way around it.
                    QuickMaskResult.NotSignedIn,
                    QuickMaskResult.DemoMode,
                    QuickMaskResult.LockRequired -> handOffToApp(handOff)
                    is QuickMaskResult.Failed -> notifyFailure(result.cause)
                }
            } finally {
                // Also release after cancellation, otherwise one cancelled tile
                // action disables Quick Mask for the rest of the process.
                createInFlight.set(false)
            }
        }
    }

    /**
     * Notification "Undo" entry. The returned [Job] completes once the delete
     * has been attempted; [QuickMaskUndoReceiver] finishes its
     * `BroadcastReceiver.goAsync` result from it, so the process keeps a live
     * component and the delete is not racing the low-memory killer. The work is
     * bounded by [QuickMaskUndoReceiver.UNDO_TIMEOUT_MS], so that completion
     * always arrives inside the goAsync window.
     */
    fun undo(id: String): Job {
        if (!latchUndone(id)) {
            // Already undone (or being undone) — dismiss the notification the
            // second tap came from and stop. Re-running destroy would fail with
            // notFound and report that failure over the truthful result.
            notifier.cancel(QuickMaskSlot.CREATED)
            return Job().apply { complete() }
        }
        return scope.launch {
            // Dismiss FIRST. The tap is the user's decision; leaving the
            // notification up until the network round-trip returns (or
            // forever, when it fails) reads as "the button did nothing".
            // The "mask created" slot is the only one carrying an Undo.
            notifier.cancel(QuickMaskSlot.CREATED)
            val success = try {
                withTimeout(QuickMaskUndoReceiver.UNDO_TIMEOUT_MS) {
                    quickMaskCreator.undo(id)
                }
            } catch (_: TimeoutCancellationException) {
                false
            } catch (cancellation: CancellationException) {
                // A cancelled attempt did not settle the mask's state. Let a
                // later notification action retry it.
                unlatchUndone(id)
                throw cancellation
            } catch (throwable: Throwable) {
                if (BuildConfig.DEBUG) Log.w(TAG, "undo failed", throwable)
                false
            }
            // Only a mask that is actually gone stays latched. A failed undo
            // releases the id, so this guard can never be the reason a later
            // attempt at the same mask is refused.
            if (!success) unlatchUndone(id)
            notifier.showUndoResult(success = success)
        }
    }

    /**
     * Hands off to the app, guarded on every path. A hand-off that fails says
     * so instead of disappearing — the user tapped something.
     */
    private suspend fun handOffToApp(handOff: AppHandOff) {
        withContext(Dispatchers.Main) {
            val opened = runCatching { handOff.open() }.getOrElse { throwable ->
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

        /**
         * Latch ceiling. A notification can only reference the most recent
         * quick-create; 256 successful undos deep, no live UI can still name
         * an evicted id.
         */
        const val UNDONE_LATCH_LIMIT = 256
    }
}
