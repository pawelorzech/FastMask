package com.fastmask.ui.list

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.fastmask.R
import com.fastmask.ui.common.UiError
import com.fastmask.ui.common.UiErrorAction
import com.fastmask.ui.components.ErrorState
import com.fastmask.ui.components.InlineErrorBanner
import com.fastmask.ui.theme.FastMaskTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ListErrorRecoverySemanticsTest {

    @get:Rule
    val compose = createComposeRule()

    private fun string(res: Int) =
        InstrumentationRegistry.getInstrumentation().targetContext.getString(res)

    private val retryError = UiError(R.string.error_network, UiErrorAction.RETRY)
    private val reauthError = UiError(R.string.error_auth, UiErrorAction.REAUTHENTICATE)

    @Test
    fun inlineErrorExposesAndRunsItsRetryAction() {
        var retries = 0
        var signIns = 0
        compose.setContent {
            FastMaskTheme {
                InlineErrorBanner(
                    error = retryError,
                    onRetry = { retries += 1 },
                    onReauthenticate = { signIns += 1 },
                )
            }
        }

        val action = compose.onNodeWithText(string(R.string.error_retry)).assertHasClickAction()
        assertEquals(
            Role.Button,
            action.fetchSemanticsNode().config.getOrNull(SemanticsProperties.Role),
        )
        action.performClick()

        assertEquals(1, retries)
        assertEquals(0, signIns)
    }

    @Test
    fun inlineErrorForARefusedTokenOffersSignInInsteadOfRetry() {
        var retries = 0
        var signIns = 0
        compose.setContent {
            FastMaskTheme {
                InlineErrorBanner(
                    error = reauthError,
                    onRetry = { retries += 1 },
                    onReauthenticate = { signIns += 1 },
                )
            }
        }

        compose.onNodeWithText(string(R.string.error_retry)).assertDoesNotExist()
        compose.onNodeWithText(string(R.string.settings_signin_with_fastmail))
            .assertHasClickAction()
            .performClick()

        assertEquals(0, retries)
        assertEquals(1, signIns)
    }

    @Test
    fun inlineErrorWithoutARetryOffersNoButtonUnlessTheTokenWasRefused() {
        compose.setContent {
            FastMaskTheme {
                InlineErrorBanner(
                    error = UiError(R.string.error_create_mask, UiErrorAction.NONE),
                    onRetry = null,
                    onReauthenticate = {},
                )
            }
        }

        compose.onNodeWithText(string(R.string.error_retry)).assertDoesNotExist()
        compose.onNodeWithText(string(R.string.settings_signin_with_fastmail)).assertDoesNotExist()
    }

    @Test
    fun emptyErrorExposesAndRunsItsRecoveryAction() {
        var retries = 0
        var signIns = 0
        compose.setContent {
            FastMaskTheme {
                ErrorState(
                    title = R.string.error_load_emails,
                    error = reauthError,
                    onRetry = { retries += 1 },
                    onReauthenticate = { signIns += 1 },
                )
            }
        }

        compose.onNodeWithText(string(R.string.settings_signin_with_fastmail))
            .assertHasClickAction()
            .performClick()

        assertEquals(0, retries)
        assertEquals(1, signIns)
    }
}
