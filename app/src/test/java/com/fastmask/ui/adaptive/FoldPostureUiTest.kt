@file:OptIn(androidx.compose.material3.adaptive.ExperimentalMaterial3AdaptiveApi::class)

package com.fastmask.ui.adaptive

import androidx.activity.ComponentActivity
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.material3.adaptive.layout.AnimatedPane
import androidx.compose.material3.adaptive.navigation.NavigableListDetailPaneScaffold
import androidx.compose.material3.adaptive.navigation.rememberListDetailPaneScaffoldNavigator
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.DeviceConfigurationOverride
import androidx.compose.ui.test.ForcedSize
import androidx.compose.ui.test.WindowSize
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.then
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.window.layout.FoldingFeature
import androidx.window.layout.WindowLayoutInfo
import androidx.window.testing.layout.FoldingFeature
import androidx.window.testing.layout.WindowLayoutInfoPublisherRule
import com.fastmask.ui.components.ConfirmDialog
import com.fastmask.ui.theme.FastMaskTheme
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.rules.TestRule
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w840dp-h800dp")
class FoldPostureUiTest {

    private val publisherRule = WindowLayoutInfoPublisherRule()
    private val composeRule = createAndroidComposeRule<ComponentActivity>()

    @get:Rule
    val testRule: TestRule = RuleChain.outerRule(publisherRule).around(composeRule)

    @Test
    fun flatUnfoldedStateRendersCenteredDialogAndConfirms() {
        var confirmed = false
        var observedTabletop = true
        composeRule.setContent {
            DeviceConfigurationOverride(
                DeviceConfigurationOverride.ForcedSize(DpSize(900.dp, 841.dp)) then
                    DeviceConfigurationOverride.WindowSize(DpSize(900.dp, 841.dp)),
            ) {
                FastMaskTheme {
                    ProvideFastMaskAdaptiveInfo {
                        observedTabletop = currentFastMaskAdaptiveInfo().posture.isTabletop
                        ConfirmDialog(
                            title = "Archive mask?",
                            message = "You can restore it later.",
                            confirmText = "Archive",
                            dismissText = "Cancel",
                            onConfirm = { confirmed = true },
                            onDismiss = {},
                        )
                    }
                }
            }
        }

        val flatFeature = FoldingFeature(
            activity = composeRule.activity,
            state = FoldingFeature.State.FLAT,
            orientation = FoldingFeature.Orientation.HORIZONTAL,
        )
        publisherRule.overrideWindowLayoutInfo(WindowLayoutInfo(listOf(flatFeature)))
        composeRule.waitForIdle()

        assertFalse(observedTabletop)
        composeRule.onNodeWithTag("hinge_aware_dialog_box").assertIsDisplayed()
        composeRule.onNodeWithText("Archive").assertIsDisplayed().performClick()
        assertTrue(confirmed)
    }

    @Test
    fun tabletopHorizontalHalfOpenedPositionsDialogAwayFromHinge() {
        var observedTabletop = false
        composeRule.setContent {
            DeviceConfigurationOverride(
                DeviceConfigurationOverride.ForcedSize(DpSize(840.dp, 800.dp)) then
                    DeviceConfigurationOverride.WindowSize(DpSize(840.dp, 800.dp)),
            ) {
                FastMaskTheme {
                    ProvideFastMaskAdaptiveInfo {
                        observedTabletop = currentFastMaskAdaptiveInfo().posture.isTabletop
                        ConfirmDialog(
                            title = "Sign out?",
                            message = "Your API token will be cleared.",
                            confirmText = "Sign out",
                            dismissText = "Cancel",
                            onConfirm = {},
                            onDismiss = {},
                        )
                    }
                }
            }
        }

        val tabletopFeature = FoldingFeature(
            activity = composeRule.activity,
            size = 20,
            state = FoldingFeature.State.HALF_OPENED,
            orientation = FoldingFeature.Orientation.HORIZONTAL,
        )
        publisherRule.overrideWindowLayoutInfo(WindowLayoutInfo(listOf(tabletopFeature)))
        composeRule.waitForIdle()

        assertTrue(observedTabletop)
        val dialogBounds = composeRule.onNodeWithTag("hinge_aware_dialog_box")
            .assertIsDisplayed()
            .fetchSemanticsNode()
            .boundsInRoot
        assertTrue(
            "Dialog in tabletop posture must sit below the center hinge: $dialogBounds",
            dialogBounds.top > 0f && dialogBounds.height > 0f,
        )
    }

    @Test
    fun bookPostureVerticalHalfOpenedSplitsPanesAndPositionsDialogAwayFromHinge() {
        var observedBook = false
        var observedSeparatingVertical = false
        composeRule.setContent {
            DeviceConfigurationOverride(
                DeviceConfigurationOverride.ForcedSize(DpSize(840.dp, 800.dp)) then
                    DeviceConfigurationOverride.WindowSize(DpSize(840.dp, 800.dp)),
            ) {
                FastMaskTheme {
                    ProvideFastMaskAdaptiveInfo {
                        val adaptive = currentFastMaskAdaptiveInfo()
                        observedBook = adaptive.posture.isBookPosture
                        observedSeparatingVertical = adaptive.posture.hasSeparatingVerticalHinge
                        val directive = FastMaskAdaptiveRules.paneDirective(adaptive)
                        val navigator = rememberListDetailPaneScaffoldNavigator<String>(scaffoldDirective = directive)
                        NavigableListDetailPaneScaffold(
                            navigator = navigator,
                            listPane = {
                                AnimatedPane(Modifier.preferredWidth(360.dp).focusGroup().testTag("book_left_pane")) {
                                    Box(Modifier.fillMaxSize()) { Text("Left Pane") }
                                }
                            },
                            detailPane = {
                                AnimatedPane(Modifier.focusGroup().testTag("book_right_pane")) {
                                    Box(Modifier.fillMaxSize()) { Text("Right Pane") }
                                }
                            },
                        )
                        ConfirmDialog(
                            title = "Archive mask?",
                            message = "You can restore it later.",
                            confirmText = "Archive",
                            dismissText = "Cancel",
                            onConfirm = {},
                            onDismiss = {},
                        )
                    }
                }
            }
        }

        val bookFeature = FoldingFeature(
            activity = composeRule.activity,
            size = 24,
            state = FoldingFeature.State.HALF_OPENED,
            orientation = FoldingFeature.Orientation.VERTICAL,
        )
        publisherRule.overrideWindowLayoutInfo(WindowLayoutInfo(listOf(bookFeature)))
        composeRule.waitForIdle()

        assertTrue(observedBook)
        assertTrue(observedSeparatingVertical)

        val leftBounds = composeRule.onNodeWithTag("book_left_pane").assertIsDisplayed().fetchSemanticsNode().boundsInRoot
        val rightBounds = composeRule.onNodeWithTag("book_right_pane").assertIsDisplayed().fetchSemanticsNode().boundsInRoot
        assertTrue(
            "Book posture panes must split cleanly without overlap: $leftBounds vs $rightBounds",
            leftBounds.right <= rightBounds.left + 1f,
        )

        val dialogBounds = composeRule.onNodeWithTag("hinge_aware_dialog_box").assertIsDisplayed().fetchSemanticsNode().boundsInRoot
        assertTrue(
            "Dialog must render with positive width on one side of the vertical hinge: $dialogBounds",
            dialogBounds.width > 0f,
        )
    }
}
