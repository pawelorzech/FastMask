package com.fastmask.ui.lock

import android.os.Bundle
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.fragment.app.FragmentActivity
import java.util.UUID

internal fun interface UnlockPromptLauncher {
    fun show(
        activity: FragmentActivity,
        title: String,
        onSuccess: () -> Unit,
        onUnavailable: () -> Unit,
        onFinished: () -> Unit,
    ): Boolean
}

class AppLockGate internal constructor(
    private val launcher: UnlockPromptLauncher,
) {
    constructor() : this(UnlockPromptLauncher(::showUnlockPrompt))

    var locked by mutableStateOf(false)
        private set

    /** Also stops a tap from stacking a second prompt, which cancels the first. */
    var promptShowing by mutableStateOf(false)
        private set

    var unlocked by mutableStateOf(false)
        private set

    var autoPromptPending by mutableStateOf(false)
        private set

    /**
     * Only a bundle from this process is trusted: below API 28 onSaveInstanceState runs
     * before onStop, so a bundle from a process that later died in the background still
     * says "unlocked" from before the ON_STOP re-lock.
     */
    fun restore(savedInstanceState: Bundle?, lockAtLaunch: Boolean) {
        val sameProcess = savedInstanceState?.getString(KEY_PROCESS_TOKEN) == processToken
        locked = if (savedInstanceState != null && sameProcess) {
            savedInstanceState.getBoolean(KEY_LOCKED, lockAtLaunch)
        } else {
            lockAtLaunch
        }
        autoPromptPending = locked
    }

    fun save(outState: Bundle) {
        // Rotating during the unlock success animation must not re-lock the user.
        outState.putBoolean(KEY_LOCKED, locked && !unlocked)
        outState.putString(KEY_PROCESS_TOKEN, processToken)
    }

    fun relock() {
        unlocked = false
        locked = true
        // System credential UI (e.g. device PIN) triggers ON_STOP; do not re-arm while
        // a prompt is active or cancelling it would re-prompt in a loop.
        if (!promptShowing) {
            autoPromptPending = true
        }
    }

    fun requestUnlock(activity: FragmentActivity, title: String) {
        if (promptShowing || unlocked) return
        launchPrompt(activity, title)
    }

    fun consumeAutoPrompt(activity: FragmentActivity, title: String) {
        if (!autoPromptPending || !locked || unlocked || promptShowing) return
        launchPrompt(activity, title)
    }

    private fun launchPrompt(activity: FragmentActivity, title: String) {
        promptShowing = true
        val launched = launcher.show(
            activity = activity,
            title = title,
            onSuccess = { unlocked = true },
            // The device can no longer authenticate at all (screen lock
            // removed): open rather than brick, as on a device without a lock.
            onUnavailable = { locked = false },
            onFinished = { promptShowing = false },
        )
        if (launched) {
            autoPromptPending = false
        }
    }

    fun onUnlockAnimationFinished() {
        if (unlocked) {
            unlocked = false
            locked = false
        }
    }

    private companion object {
        const val KEY_LOCKED = "fastmask_locked"
        const val KEY_PROCESS_TOKEN = "fastmask_process_token"

        val processToken: String = UUID.randomUUID().toString()
    }
}
