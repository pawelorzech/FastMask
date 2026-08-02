package com.fastmask.ui.list

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.fastmask.ui.theme.FastMaskTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ListErrorRecoverySemanticsTest {

    @get:Rule
    val compose = createComposeRule()

    @Test
    fun inlineErrorExposesAndRunsItsRecoveryAction() {
        var actions = 0
        compose.setContent {
            FastMaskTheme {
                InlineErrorBanner(
                    text = "Could not refresh",
                    actionLabel = "Retry",
                    onAction = { actions += 1 },
                )
            }
        }

        val action = compose.onNodeWithText("Retry").assertHasClickAction()
        assertEquals(
            Role.Button,
            action.fetchSemanticsNode().config.getOrNull(SemanticsProperties.Role),
        )
        action.performClick()

        assertEquals(1, actions)
    }

    @Test
    fun emptyErrorExposesAndRunsItsRecoveryAction() {
        var actions = 0
        compose.setContent {
            FastMaskTheme {
                ErrorBlock(
                    message = "Token rejected",
                    actionLabel = "Sign in again",
                    onAction = { actions += 1 },
                )
            }
        }

        compose.onNodeWithText("Sign in again")
            .assertHasClickAction()
            .performClick()

        assertEquals(1, actions)
    }
}
