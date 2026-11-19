package com.fastmask.ui.lock

import android.os.Bundle
import androidx.fragment.app.FragmentActivity
import io.mockk.every
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AppLockGateTest {

    private val activity = mockk<FragmentActivity>(relaxed = true)

    private fun fakeBundle(): Bundle {
        val values = mutableMapOf<String, Any?>()
        return mockk {
            every { putBoolean(any(), any()) } answers { values[firstArg()] = secondArg<Boolean>() }
            every { putString(any(), any()) } answers { values[firstArg()] = secondArg<String?>() }
            every { getBoolean(any(), any()) } answers {
                values[firstArg()] as? Boolean ?: secondArg()
            }
            every { getString(any()) } answers { values[firstArg()] as? String }
        }
    }

    @Test
    fun `restore with lockAtLaunch arms autoPromptPending and consumeAutoPrompt clears it once launched`() {
        var launches = 0
        val gate = AppLockGate { _, _, _, _, _ ->
            launches++
            true
        }

        gate.restore(savedInstanceState = null, lockAtLaunch = true)
        assertTrue(gate.locked)
        assertTrue(gate.autoPromptPending)

        gate.consumeAutoPrompt(activity, "Unlock")
        assertEquals(1, launches)
        assertTrue(gate.promptShowing)
        assertFalse(gate.autoPromptPending)

        // Second resume does not re-prompt once consumed.
        gate.consumeAutoPrompt(activity, "Unlock")
        assertEquals(1, launches)
    }

    @Test
    fun `deferred prompt resets promptShowing and keeps autoPromptPending so Unlock never wedges`() {
        var shouldDefer = true
        var launches = 0
        var finishCallback: (() -> Unit)? = null
        val gate = AppLockGate { _, _, _, _, onFinished ->
            launches++
            if (shouldDefer) {
                onFinished()
                false
            } else {
                finishCallback = onFinished
                true
            }
        }

        gate.restore(savedInstanceState = null, lockAtLaunch = true)
        gate.consumeAutoPrompt(activity, "Unlock")

        assertEquals(1, launches)
        assertFalse(gate.promptShowing)
        assertTrue(gate.autoPromptPending)

        // Manual tap while stopped also defers cleanly without locking the button.
        gate.requestUnlock(activity, "Unlock")
        assertEquals(2, launches)
        assertFalse(gate.promptShowing)
        assertTrue(gate.autoPromptPending)

        // Once resumed, the pending auto-prompt fires and clears.
        shouldDefer = false
        gate.consumeAutoPrompt(activity, "Unlock")
        assertEquals(3, launches)
        assertTrue(gate.promptShowing)
        assertFalse(gate.autoPromptPending)

        finishCallback?.invoke()
        assertFalse(gate.promptShowing)
    }

    @Test
    fun `relock re-arms autoPromptPending unless a prompt is already active`() {
        var finishCallback: (() -> Unit)? = null
        val gate = AppLockGate { _, _, _, _, onFinished ->
            finishCallback = onFinished
            true
        }

        gate.restore(savedInstanceState = null, lockAtLaunch = false)
        assertFalse(gate.locked)
        assertFalse(gate.autoPromptPending)

        // Normal ON_STOP re-locks and arms the prompt for the next resume.
        gate.relock()
        assertTrue(gate.locked)
        assertTrue(gate.autoPromptPending)

        // Prompt launches (e.g. falls back to full-screen device PIN, causing ON_STOP).
        gate.consumeAutoPrompt(activity, "Unlock")
        assertTrue(gate.promptShowing)
        assertFalse(gate.autoPromptPending)

        // ON_STOP while prompt is active must NOT re-arm autoPromptPending.
        gate.relock()
        assertFalse(gate.autoPromptPending)

        finishCallback?.invoke()
        assertFalse(gate.promptShowing)
        assertFalse(gate.autoPromptPending)
    }

    @Test
    fun `save during unlock success animation persists unlocked state across rotation`() {
        var successCallback: (() -> Unit)? = null
        val gate = AppLockGate { _, _, onSuccess, _, onFinished ->
            successCallback = onSuccess
            onFinished()
            true
        }

        gate.restore(savedInstanceState = null, lockAtLaunch = true)
        gate.consumeAutoPrompt(activity, "Unlock")
        successCallback?.invoke()
        assertTrue(gate.locked)
        assertTrue(gate.unlocked)

        val bundle = fakeBundle()
        gate.save(bundle)

        val restored = AppLockGate { _, _, _, _, _ -> true }
        restored.restore(bundle, lockAtLaunch = true)
        assertFalse(restored.locked)
        assertFalse(restored.autoPromptPending)
    }
}
