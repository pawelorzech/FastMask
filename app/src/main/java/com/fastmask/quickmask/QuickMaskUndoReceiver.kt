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
        runner.launchUndo(id = id, onFinished = pendingResult::finish)
    }
}
