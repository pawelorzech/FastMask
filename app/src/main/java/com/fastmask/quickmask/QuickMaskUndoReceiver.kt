package com.fastmask.quickmask

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class QuickMaskUndoReceiver : BroadcastReceiver() {

    @Inject
    lateinit var runner: QuickMaskRunner

    override fun onReceive(context: Context, intent: Intent) {
        val id = intent.getStringExtra(EXTRA_QUICK_MASK_ID)?.takeIf { it.isNotBlank() } ?: return
        // goAsync, not fire-and-forget: the work runs in the singleton runner's
        // scope, but once onReceive returns without a pending result the process
        // holds no live component and becomes the first candidate for the
        // low-memory killer — with the mask still on the account, the
        // notification still on screen and the user convinced they undid it.
        // The pending result keeps the process alive until the delete lands.
        val pendingResult = goAsync()
        runner.undo(id).invokeOnCompletion { pendingResult.finish() }
    }

    internal companion object {
        /** Execution window `BroadcastReceiver.goAsync()` grants before the system raises an ANR. */
        const val GO_ASYNC_BUDGET_MS = 10_000L

        /** Headroom left for `PendingResult.finish()` and scheduling once the delete is abandoned. */
        const val FINISH_MARGIN_MS = 2_000L

        // A dead network would otherwise hold the call for OkHttp's 30-second socket
        // timeouts and blow the goAsync window.
        const val UNDO_TIMEOUT_MS = GO_ASYNC_BUDGET_MS - FINISH_MARGIN_MS
    }
}
