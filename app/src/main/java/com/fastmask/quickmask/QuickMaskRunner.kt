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
        notifier.showFailure(UiErrors.messageRes(throwable, R.string.create_email_error_failed))
    }

    // The shade can tear down TileService as soon as it collapses; this scope
    // belongs to the singleton runner so create/undo work survives that.
    private val scope: CoroutineScope =
        CoroutineScope(SupervisorJob() + Dispatchers.IO + crashGuard)

    fun launchCreate(openApp: (() -> Unit)? = null) {
        scope.launch {
            val result = runCatching { quickMaskCreator.create() }
                .getOrElse { throwable ->
                    notifier.showFailure(
                        messageRes = UiErrors.messageRes(
                            throwable = throwable,
                            fallback = R.string.create_email_error_failed,
                        )
                    )
                    return@launch
                }

            when (result) {
                is QuickMaskResult.Created -> {
                    copyToClipboard(context, result.email)
                    notifier.showCreated(id = result.id)
                }
                QuickMaskResult.NotSignedIn -> openApp(openApp)
                QuickMaskResult.DemoMode -> openApp(openApp)
                QuickMaskResult.LockRequired -> {
                    // The tile lives above the lock screen; if app lock is armed,
                    // quick-create must hand off to the app's biometric gate.
                    openApp(openApp)
                }
                is QuickMaskResult.Failed -> {
                    // The domain layer reports the cause; mapping it to a
                    // localized message happens here, in the Android layer, and
                    // nowhere else.
                    notifier.showFailure(
                        messageRes = UiErrors.messageRes(
                            throwable = result.cause,
                            fallback = R.string.create_email_error_failed,
                        )
                    )
                }
            }
        }
    }

    /**
     * @param onFinished released once the delete has been attempted. The
     *   notification's Undo button hands us a `BroadcastReceiver.goAsync`
     *   result through this callback: until it is finished the process keeps a
     *   live component, so the delete is not racing the low-memory killer.
     */
    fun launchUndo(id: String, notificationId: Int, onFinished: () -> Unit = {}) {
        scope.launch {
            try {
                // Dismiss FIRST. The tap is the user's decision; leaving the
                // notification up until the network round-trip returns (or
                // forever, when it fails) reads as "the button did nothing".
                notifier.cancel(notificationId)
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
    private suspend fun openApp(openApp: (() -> Unit)?) {
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

    private companion object {
        const val TAG = "QuickMask"
    }
}
