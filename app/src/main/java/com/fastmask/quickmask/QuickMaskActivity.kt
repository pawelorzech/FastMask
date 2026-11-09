package com.fastmask.quickmask

import android.os.Bundle
import androidx.activity.ComponentActivity
import com.fastmask.core.platform.openExternalIntent
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class QuickMaskActivity : ComponentActivity() {

    @Inject
    lateinit var runner: QuickMaskRunner

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (intent?.action == ACTION_QUICK_MASK) {
            runner.createFromShortcut()
        } else {
            openExternalIntent(this, createAppLaunchIntent(this))
        }
        finish()
    }
}
