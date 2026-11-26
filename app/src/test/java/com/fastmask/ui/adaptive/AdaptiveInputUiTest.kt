package com.fastmask.ui.adaptive

import androidx.activity.ComponentActivity
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.withKeyDown
import com.fastmask.ui.theme.FastMaskTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@OptIn(ExperimentalTestApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class AdaptiveInputUiTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun keyboardShortcutsDispatchCreateSearchCopyArchiveEscapeAndArrows() {
        val requester = FocusRequester()
        var newMaskCount = 0
        var searchCount = 0
        var copyCount = 0
        var archiveCount = 0
        var escapeCount = 0
        val moves = mutableListOf<Int>()

        composeRule.setContent {
            FastMaskTheme {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .focusRequester(requester)
                        .focusable()
                        .fastMaskKeyShortcuts(
                            onNewMask = { newMaskCount++ },
                            onSearch = { searchCount++ },
                            onCopySelected = { copyCount++; true },
                            onArchiveSelected = { archiveCount++; true },
                            onEscape = { escapeCount++; true },
                            onMoveSelection = { delta -> moves += delta; true },
                        )
                        .testTag("shortcut_host"),
                )
            }
        }

        composeRule.runOnIdle { requester.requestFocus() }
        val host = composeRule.onNodeWithTag("shortcut_host")

        host.performKeyInput { withKeyDown(Key.CtrlLeft) { pressKey(Key.N) } }
        host.performKeyInput { withKeyDown(Key.CtrlLeft) { pressKey(Key.F) } }
        host.performKeyInput { pressKey(Key.Slash) }
        host.performKeyInput { withKeyDown(Key.CtrlLeft) { pressKey(Key.C) } }
        host.performKeyInput { pressKey(Key.Delete) }
        host.performKeyInput { pressKey(Key.Escape) }
        host.performKeyInput { pressKey(Key.DirectionDown) }
        host.performKeyInput { pressKey(Key.DirectionUp) }

        assertEquals(1, newMaskCount)
        assertEquals(2, searchCount)
        assertEquals(1, copyCount)
        assertEquals(1, archiveCount)
        assertEquals(1, escapeCount)
        assertEquals(listOf(1, -1), moves)
    }

    @Test
    fun actionTooltipPreservesIconButtonClickAndAccessibility() {
        var clicked = false
        composeRule.setContent {
            FastMaskTheme {
                ActionTooltip(label = "Settings") {
                    IconButton(onClick = { clicked = true }) {
                        Icon(Icons.Filled.Settings, contentDescription = "Settings")
                    }
                }
            }
        }

        composeRule.onNodeWithContentDescription("Settings").assertIsDisplayed().performClick()
        assertTrue(clicked)
    }
}
