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

fun interface AppHandOff {
    fun open(): Boolean
}

@Singleton
class QuickMaskRunner @Inject constructor(
    @ApplicationContext private val context: Context,
    private val quickMaskCreator: QuickMaskCreator,
    private val notifier: QuickMaskNotifier,
    @IoDispatcher private val dispatcher: CoroutineDispatcher,
) {

    /**
     * A throwable escaping these coroutines would take the PROCESS down. Realistic
     * sources are the hand-off to the app (`startActivityAndCollapse` throws once
     * the tile has left its click window; a background activity launch can be
     * refused) and storage errors thrown before `create()`'s own runCatching.
     */
    private val crashGuard = CoroutineExceptionHandler { _, throwable ->
        Log.w(TAG, "quick mask work failed", throwable)
        notifyFailure(throwable)
    }

    // The shade can tear down TileService as soon as it collapses; this scope
    // belongs to the singleton runner so create/undo work survives that.
    private val scope: CoroutineScope =
        CoroutineScope(SupervisorJob() + dispatcher + crashGuard)

    // A Quick Settings tile gives no feedback until the notification arrives, so
    // impatient taps would otherwise create several real masks that share one
    // notification.
    private val createInFlight = AtomicBoolean(false)

    /**
     * Ids whose undo has already been accepted. The notification's Undo button
     * stays on screen for tens of milliseconds after the first tap, so two
     * broadcasts would run `destroy` twice for one id; the second comes back
     * `notFound`, is reported as a failure and overwrites the truthful "Undone".
     */
    private val undoneLock = Any()
    private val undoneIds = LinkedHashMap<String, Unit>(64)

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

    fun createFromTile(handOff: AppHandOff) = launchCreate(handOff)

    fun createFromShortcut() = launchCreate {
        openExternalIntent(context, createAppLaunchIntent(context))
    }

    private fun launchCreate(handOff: AppHandOff) {
        if (!createInFlight.compareAndSet(false, true)) {
            return
        }
        scope.launch {
            try {
                // Swallowing cancellation could post a stale failure after Android
                // has cancelled the work.
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
                createInFlight.set(false)
            }
        }
    }

    /**
     * The returned [Job] completes once the delete has been attempted; the receiver
     * finishes its `BroadcastReceiver.goAsync` result from it, so the process keeps
     * a live component and the delete is not racing the low-memory killer.
     */
    fun undo(id: String): Job {
        if (!latchUndone(id)) {
            notifier.cancel(QuickMaskSlot.CREATED)
            return Job().apply { complete() }
        }
        return scope.launch {
            // Dismiss FIRST. The tap is the user's decision; leaving the
            // notification up until the network round-trip returns (or
            // forever, when it fails) reads as "the button did nothing".
            notifier.cancel(QuickMaskSlot.CREATED)
            val success = try {
                withTimeout(QuickMaskUndoReceiver.UNDO_TIMEOUT_MS) {
                    quickMaskCreator.undo(id)
                }
            } catch (_: TimeoutCancellationException) {
                false
            } catch (cancellation: CancellationException) {
                unlatchUndone(id)
                throw cancellation
            } catch (throwable: Throwable) {
                if (BuildConfig.DEBUG) Log.w(TAG, "undo failed", throwable)
                false
            }
            if (!success) unlatchUndone(id)
            notifier.showUndoResult(success = success)
        }
    }

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

    private fun notifyFailure(throwable: Throwable?) {
        notifier.showFailure(UiErrors.messageRes(throwable, R.string.error_create_mask))
    }

    private companion object {
        const val TAG = "QuickMask"

        const val UNDONE_LATCH_LIMIT = 256
    }
}
