package com.fastmask.ui.lock

import android.os.Bundle
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.fragment.app.FragmentActivity
import java.util.UUID

/**
 * The biometric gate in front of all content. While [locked], nothing behind it
 * is composed.
 *
 * [promptShowing] and [unlocked] only drive LockScreen's motion; the gate opens
 * when the success animation reports it is done, never earlier.
 */
class AppLockGate {
    var locked by mutableStateOf(false)
        private set

    /** The system prompt is on screen. Also stops a tap from stacking a second prompt, which cancels the first. */
    var promptShowing by mutableStateOf(false)
        private set

    /** Authentication passed and the success animation is playing. */
    var unlocked by mutableStateOf(false)
        private set

    /**
     * A rotation keeps the previous instance's answer, so an open app does not
     * ask again. Only a bundle from this process is trusted: below API 28
     * onSaveInstanceState runs before onStop, so a bundle from a process that
     * later died in the background still says "unlocked" from before the
     * ON_STOP re-lock.
     */
    fun restore(savedInstanceState: Bundle?, lockAtLaunch: Boolean) {
        val sameProcess = savedInstanceState?.getString(KEY_PROCESS_TOKEN) == processToken
        locked = if (savedInstanceState != null && sameProcess) {
            savedInstanceState.getBoolean(KEY_LOCKED, lockAtLaunch)
        } else {
            lockAtLaunch
        }
    }

    fun save(outState: Bundle) {
        outState.putBoolean(KEY_LOCKED, locked)
        outState.putString(KEY_PROCESS_TOKEN, processToken)
    }

    /** Leaving the foreground re-locks, and cancels a success animation still in flight. */
    fun relock() {
        unlocked = false
        locked = true
    }

    fun requestUnlock(activity: FragmentActivity, title: String) {
        if (promptShowing) return
        promptShowing = true
        showUnlockPrompt(
            activity = activity,
            title = title,
            onSuccess = { unlocked = true },
            // The device can no longer authenticate at all (screen lock
            // removed): open rather than brick, as on a device without a lock.
            onUnavailable = { locked = false },
            // Released on every outcome, cancel and lockout included, so the
            // retry button can never be left dead.
            onFinished = { promptShowing = false },
        )
    }

    fun onUnlockAnimationFinished() {
        // A re-lock during the animation has already cleared [unlocked].
        if (unlocked) {
            unlocked = false
            locked = false
        }
    }

    private companion object {
        const val KEY_LOCKED = "fastmask_locked"
        const val KEY_PROCESS_TOKEN = "fastmask_process_token"

        /** Identifies this OS process; a bundle carrying another one was written before a process death. */
        val processToken: String = UUID.randomUUID().toString()
    }
}
