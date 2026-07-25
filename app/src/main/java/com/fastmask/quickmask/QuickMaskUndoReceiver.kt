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
        val notificationId = intent.getIntExtra(EXTRA_NOTIFICATION_ID, QUICK_MASK_NOTIFICATION_ID)
        // The receiver returns immediately, but the singleton runner owns the
        // real work in a process-wide scope that outlives this onReceive call.
        runner.launchUndo(id = id, notificationId = notificationId)
    }
}
