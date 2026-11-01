package com.fastmask.ui.auth

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Source-level guard for the privacy rule on the login screen: the clipboard is
 * read only from the explicit Paste tap. Android 12+ shows a "pasted from your
 * clipboard" toast on every read, so a lifecycle-driven read is a visible
 * privacy regression even when nothing matches.
 */
class LoginClipboardPrivacyTest {

    private val screen: String =
        File("src/main/java/com/fastmask/ui/auth/LoginScreen.kt").readText()

    @Test
    fun loginScreenReadsTheClipboardExactlyOnce() {
        val reads = Regex("""clipboardManager\.getText\(""").findAll(screen).count()
        assertEquals("exactly one clipboard read is allowed on the login screen", 1, reads)
    }

    @Test
    fun theOnlyClipboardReadSitsInThePasteHandler() {
        val read = screen.lines().single { it.contains("clipboardManager.getText(") }
        assertTrue("clipboard read must be inside onPaste: $read", read.contains("onPaste ="))
        assertTrue(read.contains("viewModel.onTokenPasted("))
    }

    @Test
    fun noLifecycleObserverTouchesTheClipboard() {
        assertFalse("no ON_RESUME observer on the login screen", screen.contains("ON_RESUME"))
        assertFalse(screen.contains("LifecycleEventObserver"))
    }
}
