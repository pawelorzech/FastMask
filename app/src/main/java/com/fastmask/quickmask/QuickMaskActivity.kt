package com.fastmask.quickmask

import android.os.Bundle
import androidx.activity.ComponentActivity
import com.fastmask.ui.common.openExternalIntent
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

/**
 * Invisible trampoline behind the launcher shortcut: creates one mask and gets
 * out of the way.
 *
 * Two things keep it from being a way for other apps to write to the user's
 * Fastmail account:
 *
 * 1. `android:exported="false"` in the manifest. Static shortcuts do not need
 *    the export — the launcher starts them through `LauncherApps.startShortcut`,
 *    which the system runs as the PUBLISHING package
 *    (`ActivityTaskManagerInternal.startActivitiesAsPackage`), so the caller
 *    identity is FastMask's own uid.
 * 2. The action check below. Even an in-package launch only creates when it
 *    carries [ACTION_QUICK_MASK]; anything else just opens the app.
 */
@AndroidEntryPoint
class QuickMaskActivity : ComponentActivity() {

    @Inject
    lateinit var runner: QuickMaskRunner

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (QuickMaskPolicy.isQuickCreateLaunch(intent?.action)) {
            runner.launchCreate()
        } else {
            // Never create from an intent we did not author. Opening the app is
            // the safe reading of "someone started this activity".
            openExternalIntent(this, createAppLaunchIntent(this))
        }
        finish()
    }
}
