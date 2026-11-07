package com.fastmask.ui.accessibility

import android.content.Context
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.remember
import androidx.compose.ui.test.hasAnyDescendant
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onFirst
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.fastmask.R
import com.fastmask.domain.model.Accent
import com.fastmask.testutil.isHeadingNode
import com.fastmask.testutil.isPoliteLiveRegion
import com.fastmask.testutil.isSelectableGroup
import com.fastmask.testutil.mask
import com.fastmask.testutil.string
import com.fastmask.ui.auth.LoginContent
import com.fastmask.ui.auth.LoginError
import com.fastmask.ui.auth.LoginUiState
import com.fastmask.ui.common.UiError
import com.fastmask.ui.common.UiErrorAction
import com.fastmask.ui.components.EnabledDisabledToggle
import com.fastmask.ui.detail.Hero
import com.fastmask.ui.list.MaskedEmailListContent
import com.fastmask.ui.list.MaskedEmailListUiState
import com.fastmask.ui.lock.LockContent
import com.fastmask.ui.settings.ColorSwatchRow
import com.fastmask.ui.theme.FastMaskTheme
import com.fastmask.ui.welcome.WelcomeContent
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The accessibility promises of whole screens, checked on the real composables rather
 * than on their source text: every screen title is a heading, the two option groups are
 * selectable groups, and every state that changes under the user's finger has a polite
 * announcement path.
 *
 * Replaces the source-grep `AccessibilitySemanticsWiringTest` (unit). The modifiers
 * themselves are covered in isolation by [AccessibilitySemanticsTest].
 */
@RunWith(AndroidJUnit4::class)
class ScreenAccessibilityTest {

    @get:Rule
    val compose = createComposeRule()

    private val context = ApplicationProvider.getApplicationContext<Context>()

    // --- screen titles are headings ------------------------------------------------

    @Test
    fun loginTitleIsAHeading() {
        compose.setContent { FastMaskTheme { loginContent(LoginUiState()) } }

        compose.onAllNodes(isHeadingNode and hasText(string(R.string.login_hero_accent), substring = true))
            .onFirst().assertExists()
    }

    @Test
    fun welcomeTitleIsAHeading() {
        compose.setContent {
            FastMaskTheme {
                WelcomeContent(isLoading = false, errorRes = null, onSignIn = {}, onEnterDemo = {})
            }
        }

        compose.onAllNodes(isHeadingNode and hasText(string(R.string.app_name))).onFirst().assertExists()
    }

    @Test
    fun detailHeroAddressIsAHeading() {
        val address = "hero.title@example.com"
        compose.setContent { FastMaskTheme { Hero(mask(address)) } }

        compose.onAllNodes(isHeadingNode and hasText(address)).onFirst().assertExists()
    }

    @Test
    fun lockTitleIsAHeading() {
        compose.setContent {
            FastMaskTheme { LockContent(onUnlockClick = {}, promptActive = false, unlocked = false) }
        }

        compose.onAllNodes(isHeadingNode and hasText(string(R.string.app_name))).onFirst().assertExists()
    }

    // --- option groups are selectable groups ---------------------------------------

    @Test
    fun enabledDisabledToggleIsOneSelectableGroupOfBothOptions() {
        compose.setContent {
            FastMaskTheme {
                EnabledDisabledToggle(activeSelected = true, onEnable = {}, onDisable = {})
            }
        }

        compose.onAllNodes(
            isSelectableGroup and
                hasAnyDescendant(hasText(string(R.string.state_enabled))) and
                hasAnyDescendant(hasText(string(R.string.state_disabled))),
        ).onFirst().assertExists()
    }

    @Test
    fun colourSwatchesAreASelectableGroup() {
        compose.setContent {
            FastMaskTheme {
                ColorSwatchRow(selected = Accent.DYNAMIC, isPro = true, onSelect = {}, onLockedTap = {})
            }
        }

        compose.onAllNodes(isSelectableGroup).onFirst().assertExists()
    }

    // --- dynamic state has a polite announcement path ------------------------------

    @Test
    fun loginWarningIsAnnouncedPolitely() {
        val warning = string(R.string.login_token_warning_shape)
        compose.setContent {
            FastMaskTheme {
                loginContent(LoginUiState(token = "not-a-token", warningRes = R.string.login_token_warning_shape))
            }
        }

        compose.onAllNodes(hasText(warning) and isPoliteLiveRegion, useUnmergedTree = true)
            .onFirst().assertExists()
    }

    @Test
    fun loginFieldErrorIsAnnouncedPolitely() {
        val error = string(R.string.error_auth)
        compose.setContent {
            FastMaskTheme { loginContent(LoginUiState(token = "x", error = LoginError.Message(R.string.error_auth))) }
        }

        compose.onAllNodes(hasText(error) and isPoliteLiveRegion, useUnmergedTree = true)
            .onFirst().assertExists()
    }

    @Test
    fun loginBrowserFailureHintIsAnnouncedPolitely() {
        val hint = string(R.string.login_open_browser_failed)
        compose.setContent {
            FastMaskTheme { loginContent(LoginUiState(), settingsOpenFailed = true, initiallyExpanded = true) }
        }

        compose.onAllNodes(hasText(hint) and isPoliteLiveRegion, useUnmergedTree = true)
            .onFirst().assertExists()
    }

    @Test
    fun listLoadingStateIsAnnouncedPolitely() {
        compose.setContent {
            FastMaskTheme { listContent(MaskedEmailListUiState(isLoading = true)) }
        }

        compose.onAllNodes(
            hasContentDescription(string(R.string.state_working)) and isPoliteLiveRegion,
            useUnmergedTree = true,
        ).onFirst().assertExists()
    }

    @Test
    fun listErrorStateIsAnnouncedPolitely() {
        compose.setContent {
            FastMaskTheme {
                listContent(
                    MaskedEmailListUiState(error = UiError(R.string.error_network, UiErrorAction.RETRY)),
                )
            }
        }

        compose.onAllNodes(
            hasAnyDescendant(hasText(string(R.string.error_network))) and isPoliteLiveRegion,
            useUnmergedTree = true,
        ).onFirst().assertExists()
    }

    @Test
    fun searchResultCountShowsFilteredOverTotalAndIsAnnouncedPolitely() {
        val emails = listOf(
            mask("alpha.one@example.com"),
            mask("beta.two@example.com").copy(id = "2"),
            mask("gamma.three@example.com").copy(id = "3"),
        )
        // One of the three matches the query: the text must read "1/3", not "3/3" or "1/1".
        val expected = context.getString(R.string.email_list_result_count, 1, 3)
        compose.setContent {
            FastMaskTheme {
                listContent(MaskedEmailListUiState(emails = emails, searchQuery = "alpha"))
            }
        }

        compose.onAllNodes(hasText(expected) and isPoliteLiveRegion, useUnmergedTree = true)
            .onFirst().assertExists()
    }

    // --- helpers -------------------------------------------------------------------

    @androidx.compose.runtime.Composable
    private fun loginContent(
        state: LoginUiState,
        settingsOpenFailed: Boolean = false,
        initiallyExpanded: Boolean = false,
    ) = LoginContent(
        uiState = state,
        showToken = false,
        settingsOpenFailed = settingsOpenFailed,
        onTokenChange = {},
        onToggleShowToken = {},
        onPaste = {},
        onLogin = {},
        onOpenTokenSettings = {},
        onEnterDemo = {},
        onBack = {},
        initiallyExpanded = initiallyExpanded,
    )

    @androidx.compose.runtime.Composable
    private fun listContent(state: MaskedEmailListUiState) = MaskedEmailListContent(
        uiState = state,
        snackbarHostState = remember { SnackbarHostState() },
        listState = rememberLazyListState(),
        highlightEmail = null,
        onSearchQueryChange = {},
        onFilterSelect = {},
        onRowOpen = {},
        onRowCopy = {},
        onRetry = {},
        onReauthenticate = {},
        onRefresh = {},
        onNavigateToCreate = {},
        onRandomMask = {},
        onNavigateToSettings = {},
        onTutorialBounds = { _, _ -> },
        demoBanner = {},
    )
}
