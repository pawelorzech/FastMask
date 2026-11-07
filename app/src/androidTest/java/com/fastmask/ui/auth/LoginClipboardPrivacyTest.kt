package com.fastmask.ui.auth

import androidx.activity.ComponentActivity
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.ClipboardManager
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.text.AnnotatedString
import androidx.lifecycle.Lifecycle
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.fastmask.R
import com.fastmask.testutil.FakeSessionRepository
import com.fastmask.testutil.string
import com.fastmask.ui.theme.FastMaskTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The privacy rule on the login screen: the clipboard is read only from the explicit
 * Paste tap. Android 12+ shows a "pasted from your clipboard" toast on every read, so a
 * read driven by composition or the lifecycle is a visible privacy regression even when
 * nothing matches.
 *
 * Replaces the source-grep `LoginClipboardPrivacyTest` (unit): this renders the real
 * [LoginScreen] with a clipboard that counts reads.
 */
@RunWith(AndroidJUnit4::class)
class LoginClipboardPrivacyTest {

    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private class CountingClipboard(
        private val real: ClipboardManager,
        private val contents: String,
    ) : ClipboardManager by real {
        var reads = 0
            private set

        override fun getText(): AnnotatedString? {
            reads += 1
            return AnnotatedString(contents)
        }

        override fun hasText(): Boolean {
            reads += 1
            return true
        }
    }

    private lateinit var clipboard: CountingClipboard
    private lateinit var viewModel: LoginViewModel

    private fun showLogin(contents: String = "fmu1-pasted-token") {
        viewModel = LoginViewModel(FakeSessionRepository())
        compose.setContent {
            val real = LocalClipboardManager.current
            // remember: a recomposition must not swap in a fresh counter.
            clipboard = remember { CountingClipboard(real, contents) }
            CompositionLocalProvider(LocalClipboardManager provides clipboard) {
                FastMaskTheme {
                    LoginScreen(
                        onLoginSuccess = {},
                        onEnterDemo = {},
                        viewModel = viewModel,
                    )
                }
            }
        }
        compose.waitForIdle()
    }

    @Test
    fun composingTheScreenDoesNotTouchTheClipboard() {
        showLogin()

        assertEquals("rendering the login screen must not read the clipboard", 0, clipboard.reads)
    }

    @Test
    fun resumingTheScreenDoesNotTouchTheClipboard() {
        showLogin()

        compose.activityRule.scenario.moveToState(Lifecycle.State.STARTED)
        compose.activityRule.scenario.moveToState(Lifecycle.State.RESUMED)
        compose.waitForIdle()

        assertEquals("a lifecycle resume must not read the clipboard", 0, clipboard.reads)
    }

    @Test
    fun tappingPasteReadsTheClipboardOnceAndHandsItToTheViewModel() {
        showLogin(contents = "fmu1-pasted-token")

        compose.onNodeWithText(string(R.string.login_paste_button)).performClick()
        compose.waitForIdle()

        assertEquals("one tap, one clipboard read", 1, clipboard.reads)
        assertEquals("fmu1-pasted-token", viewModel.uiState.value.token)
    }
}
