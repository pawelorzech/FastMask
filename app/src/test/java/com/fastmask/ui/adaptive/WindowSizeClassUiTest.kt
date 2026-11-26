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
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.then
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import com.fastmask.R
import com.fastmask.ui.create.CreateMaskedEmailContent
import com.fastmask.ui.create.CreateMaskedEmailUiState
import com.fastmask.ui.theme.FastMaskTheme
import com.fastmask.ui.welcome.WelcomeContent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class WindowSizeClassUiTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private fun setSizedContent(size: DpSize, content: @Composable () -> Unit) {
        composeRule.setContent {
            DeviceConfigurationOverride(
                DeviceConfigurationOverride.ForcedSize(size) then DeviceConfigurationOverride.WindowSize(size),
            ) {
                FastMaskTheme {
                    ProvideFastMaskAdaptiveInfo {
                        content()
                    }
                }
            }
        }
    }

    @Composable
    private fun CanonicalTwoPaneProbe() {
        val adaptive = currentFastMaskAdaptiveInfo()
        val directive = FastMaskAdaptiveRules.paneDirective(adaptive)
        val navigator = rememberListDetailPaneScaffoldNavigator<String>(scaffoldDirective = directive)
        NavigableListDetailPaneScaffold(
            navigator = navigator,
            listPane = {
                AnimatedPane(Modifier.preferredWidth(380.dp).focusGroup().testTag("probe_list_pane")) {
                    Box(Modifier.fillMaxSize()) { Text("Masks List Pane") }
                }
            },
            detailPane = {
                AnimatedPane(Modifier.focusGroup().testTag("probe_detail_pane")) {
                    Box(Modifier.fillMaxSize()) { Text("Mask Detail Pane") }
                }
            },
        )
    }

    @Test
    fun compact411x891KeepsPhoneModeAndSinglePane() {
        var observed: FastMaskAdaptiveInfo? = null
        setSizedContent(DpSize(411.dp, 891.dp)) {
            observed = currentFastMaskAdaptiveInfo()
            CanonicalTwoPaneProbe()
        }

        val info = requireNotNull(observed)
        assertEquals(FastMaskWidthClass.COMPACT, info.widthClass)
        assertFalse(FastMaskAdaptiveRules.usesWorkspace(info))
        composeRule.onNodeWithTag("probe_list_pane").assertIsDisplayed()
        composeRule.onNodeWithTag("probe_detail_pane").assertDoesNotExist()
    }

    @Test
    fun landscapePhone891x411StaysOnPhonePathGuard() {
        var observed: FastMaskAdaptiveInfo? = null
        setSizedContent(DpSize(891.dp, 411.dp)) {
            observed = currentFastMaskAdaptiveInfo()
            WelcomeContent(isLoading = false, errorRes = null, onSignIn = {}, onEnterDemo = {})
        }

        val info = requireNotNull(observed)
        assertEquals(FastMaskHeightClass.COMPACT, info.heightClass)
        assertFalse("landscape phone must never activate the workspace", FastMaskAdaptiveRules.usesWorkspace(info))
        val cta = composeRule.activity.getString(R.string.welcome_signin_cta)
        composeRule.onNodeWithText(cta).performScrollTo().assertIsDisplayed()
    }

    @Test
    fun shortLandscape580x340KeepsCreateFormScrollableAndReachable() {
        var created = false
        setSizedContent(DpSize(580.dp, 340.dp)) {
            CreateMaskedEmailContent(
                uiState = CreateMaskedEmailUiState(),
                banner = {},
                onClose = {},
                onPrefixChange = {},
                onDomainChange = {},
                onDescriptionChange = {},
                onUrlChange = {},
                onStateChange = {},
                onCreate = { created = true },
                onReauthenticate = {},
            )
        }

        val createBtn = composeRule.activity.getString(R.string.create_email_button)
        composeRule.onNodeWithText(createBtn).performScrollTo().assertIsDisplayed().performClick()
        assertTrue(created)
    }

    @Test
    fun medium673x841ActivatesWorkspaceWithSinglePaneWhenFlat() {
        var observed: FastMaskAdaptiveInfo? = null
        setSizedContent(DpSize(673.dp, 841.dp)) {
            observed = currentFastMaskAdaptiveInfo()
            CanonicalTwoPaneProbe()
        }

        val info = requireNotNull(observed)
        assertEquals(FastMaskWidthClass.MEDIUM, info.widthClass)
        assertTrue(FastMaskAdaptiveRules.usesWorkspace(info))
        composeRule.onNodeWithTag("probe_list_pane").assertIsDisplayed()
        composeRule.onNodeWithTag("probe_detail_pane").assertDoesNotExist()
    }

    @Test
    fun expanded900x841RendersListAndDetailSideBySideWithoutOverlap() {
        var observed: FastMaskAdaptiveInfo? = null
        setSizedContent(DpSize(900.dp, 841.dp)) {
            observed = currentFastMaskAdaptiveInfo()
            CanonicalTwoPaneProbe()
        }

        val info = requireNotNull(observed)
        assertEquals(FastMaskWidthClass.EXPANDED, info.widthClass)
        assertTrue(FastMaskAdaptiveRules.usesWorkspace(info))
        val listBounds = composeRule.onNodeWithTag("probe_list_pane").assertIsDisplayed().fetchSemanticsNode().boundsInRoot
        val detailBounds = composeRule.onNodeWithTag("probe_detail_pane").assertIsDisplayed().fetchSemanticsNode().boundsInRoot
        assertTrue(
            "List and Detail panes must sit side by side without overlap: $listBounds vs $detailBounds",
            listBounds.right <= detailBounds.left + 1f,
        )
    }

    @Test
    fun large1280x800RendersDualPaneWithoutOverlap() {
        var observed: FastMaskAdaptiveInfo? = null
        setSizedContent(DpSize(1280.dp, 800.dp)) {
            observed = currentFastMaskAdaptiveInfo()
            CanonicalTwoPaneProbe()
        }

        val info = requireNotNull(observed)
        assertEquals(FastMaskWidthClass.LARGE, info.widthClass)
        assertTrue(FastMaskAdaptiveRules.usesWorkspace(info))
        val listBounds = composeRule.onNodeWithTag("probe_list_pane").assertIsDisplayed().fetchSemanticsNode().boundsInRoot
        val detailBounds = composeRule.onNodeWithTag("probe_detail_pane").assertIsDisplayed().fetchSemanticsNode().boundsInRoot
        assertTrue(
            "List and Detail panes must sit side by side without overlap: $listBounds vs $detailBounds",
            listBounds.right <= detailBounds.left + 1f,
        )
    }
}
