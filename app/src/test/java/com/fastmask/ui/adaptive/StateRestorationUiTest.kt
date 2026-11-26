package com.fastmask.ui.adaptive

import androidx.activity.ComponentActivity
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.lifecycle.SavedStateHandle
import com.fastmask.R
import com.fastmask.domain.model.EmailState
import com.fastmask.domain.model.Session
import com.fastmask.domain.review.ReviewTrigger
import com.fastmask.testutil.FakeMaskedEmailRepository
import com.fastmask.testutil.FakeSessionRepository
import com.fastmask.testutil.mask
import com.fastmask.ui.create.CreateMaskedEmailContent
import com.fastmask.ui.create.CreateMaskedEmailViewModel
import com.fastmask.ui.list.EmailFilter
import com.fastmask.ui.list.MaskedEmailListViewModel
import com.fastmask.ui.settings.SettingsCallbacks
import com.fastmask.ui.settings.SettingsContent
import com.fastmask.ui.settings.SettingsViewState
import com.fastmask.ui.theme.FastMaskTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class StateRestorationUiTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun settingsLanguageDialogSurvivesComposeStateRestoration() {
        val restorationTester = StateRestorationTester(composeRule)

        restorationTester.setContent {
            FastMaskTheme {
                ProvideFastMaskAdaptiveInfo {
                    SettingsContent(
                        state = SettingsViewState(),
                        snackbarHostState = remember { SnackbarHostState() },
                        callbacks = SettingsCallbacks(),
                    )
                }
            }
        }

        val languageLabel = composeRule.activity.getString(R.string.settings_language)
        composeRule.onNodeWithText(languageLabel).performScrollTo().performClick()
        val pickerTitle = composeRule.activity.getString(R.string.settings_select_language)
        composeRule.onNode(hasText(pickerTitle) and hasAnyAncestor(isDialog())).assertIsDisplayed()

        restorationTester.emulateSavedInstanceStateRestore()

        composeRule.onNode(hasText(pickerTitle) and hasAnyAncestor(isDialog())).assertIsDisplayed()
    }

    @Test
    fun createFormDraftSurvivesSavedStateHandleAndStateRestoration() {
        val restorationTester = StateRestorationTester(composeRule)
        val handle = SavedStateHandle()
        val repo = FakeMaskedEmailRepository()
        val trigger = object : ReviewTrigger {
            override suspend fun onMaskCreated() = Unit
        }

        restorationTester.setContent {
            val vm = remember { CreateMaskedEmailViewModel(handle, repo, trigger) }
            val state by vm.uiState.collectAsState()
            FastMaskTheme {
                ProvideFastMaskAdaptiveInfo {
                    CreateMaskedEmailContent(
                        uiState = state,
                        banner = {},
                        onClose = {},
                        onPrefixChange = vm::onPrefixChange,
                        onDomainChange = vm::onDomainChange,
                        onDescriptionChange = vm::onDescriptionChange,
                        onUrlChange = vm::onUrlChange,
                        onStateChange = vm::onStateChange,
                        onCreate = vm::create,
                        onReauthenticate = {},
                    )
                }
            }
        }

        // Simulate user edits saved in SavedStateHandle across recreation.
        val firstVm = CreateMaskedEmailViewModel(handle, repo, trigger)
        firstVm.onPrefixChange("news")
        firstVm.onDomainChange("example.org")
        firstVm.onDescriptionChange("Weekly digest")
        firstVm.onStateChange(EmailState.DISABLED)

        restorationTester.emulateSavedInstanceStateRestore()

        val recreated = CreateMaskedEmailViewModel(handle, repo, trigger)
        assertEquals("news", recreated.uiState.value.emailPrefix)
        assertEquals("example.org", recreated.uiState.value.forDomain)
        assertEquals("Weekly digest", recreated.uiState.value.description)
        assertEquals(EmailState.DISABLED, recreated.uiState.value.initialState)
    }

    @Test
    fun listSearchAndFilterSurviveSavedStateHandleRecreation() {
        val handle = SavedStateHandle()
        val repo = FakeMaskedEmailRepository(emails = listOf(mask("m1", description = "GitHub")))
        val session = FakeSessionRepository(initial = Session.ACCOUNT)

        val first = MaskedEmailListViewModel(repo, session, handle)
        first.onSearchQueryChange("github")
        first.onFilterChange(EmailFilter.ENABLED)

        val restored = MaskedEmailListViewModel(repo, session, handle)
        assertEquals("github", restored.uiState.value.searchQuery)
        assertEquals(EmailFilter.ENABLED, restored.uiState.value.selectedFilter)
    }
}
