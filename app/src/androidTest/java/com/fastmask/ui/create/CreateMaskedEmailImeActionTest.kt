package com.fastmask.ui.create

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performImeAction
import androidx.compose.ui.test.requestFocus
import androidx.compose.ui.text.input.ImeAction
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.fastmask.MainActivity
import com.fastmask.R
import com.fastmask.data.local.SettingsDataStore
import com.fastmask.data.local.TokenStorage
import com.fastmask.domain.model.AppMode
import com.fastmask.domain.repository.DemoSession
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import javax.inject.Inject
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Regression coverage for gh-27-ime ("Create form: no ImeAction.Next between
 * fields"), driven through the REAL create screen —
 * [com.fastmask.ui.create.CreateMaskedEmailScreen] — inside the real Activity
 * and Hilt graph, the same way [com.fastmask.MainFlowsTest] drives the list.
 *
 * The previous version of this file was rejected: it called `DesignInput`
 * directly and passed `KeyboardOptions(imeAction = ImeAction.Next)` /
 * `KeyboardActions(...)` IN FROM THE TEST ITSELF, then asserted `DesignInput`
 * forwarded what the test had just handed it. `DesignInput` never changed as
 * part of this fix, so that test passed identically against the unfixed
 * screen — it tested nothing about the ticket.
 *
 * `CreateMaskedEmailScreen` has no stateless, parameter-driven content
 * composable to call directly — its top-level composable takes a Hilt
 * `hiltViewModel()` default and every field is wired straight to
 * `viewModel::onXChange`/`uiState.x`. So this test launches the real app (as
 * `MainFlowsTest` does), opens the create screen, and reads back what THAT
 * screen actually wired onto each field: no `KeyboardOptions` or
 * `KeyboardActions` value is constructed anywhere in this file.
 */
@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
class CreateMaskedEmailImeActionTest {

    @get:Rule(order = 0)
    val hiltRule = HiltAndroidRule(this)

    // Empty rule, not createAndroidComposeRule: the Activity reads persisted
    // state in onCreate to pick its start destination, so app state has to be
    // wiped BEFORE it launches, not after (see MainFlowsTest).
    @get:Rule(order = 1)
    val composeRule = createEmptyComposeRule()

    @Inject
    lateinit var settings: SettingsDataStore

    @Inject
    lateinit var tokenStorage: TokenStorage

    @Inject
    lateinit var demoSession: DemoSession

    private lateinit var scenario: ActivityScenario<MainActivity>

    private val context = ApplicationProvider.getApplicationContext<android.content.Context>()

    private fun string(id: Int) = context.getString(id)

    @Before
    fun launchOnAFreshInstallAndOpenTheCreateScreen() {
        hiltRule.inject()
        runBlocking {
            tokenStorage.clearToken()
            settings.setAppMode(AppMode.REAL)
            settings.setTutorialCompleted(false)
            demoSession.reset()
        }
        scenario = ActivityScenario.launch(MainActivity::class.java)
        composeRule.waitUntil(timeoutMillis = 10_000) {
            composeRule.onAllNodesWithText(string(R.string.welcome_demo_cta))
                .fetchSemanticsNodes().isNotEmpty()
        }

        composeRule.onNodeWithText(string(R.string.welcome_demo_cta)).performClick()
        composeRule.waitUntil(timeoutMillis = 10_000) {
            composeRule.onAllNodesWithText(string(R.string.tutorial_skip))
                .fetchSemanticsNodes().isNotEmpty() ||
                composeRule.onAllNodesWithText(string(R.string.email_list_title))
                    .fetchSemanticsNodes().isNotEmpty()
        }
        val skip = composeRule.onAllNodesWithText(string(R.string.tutorial_skip))
        if (skip.fetchSemanticsNodes().isNotEmpty()) skip.onFirst().performClick()
        composeRule.waitUntil(timeoutMillis = 10_000) {
            composeRule.onAllNodesWithText(string(R.string.email_list_title))
                .fetchSemanticsNodes().isNotEmpty()
        }

        composeRule
            .onNodeWithContentDescription(string(R.string.email_list_create_description))
            .performClick()
        composeRule.waitUntil(timeoutMillis = 10_000) {
            composeRule.onAllNodesWithText(string(R.string.create_email_title))
                .fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.waitForIdle()
    }

    @After
    fun tearDown() {
        if (::scenario.isInitialized) scenario.close()
    }

    /**
     * DesignInput attaches `contentDescription = label` directly to the
     * BasicTextField node (see DesignInput.kt), and `keyboardOptions` is a
     * BasicTextField constructor parameter, so both semantics — the name and
     * the ImeAction — land on that same node. Reading ImeAction off the node
     * found by its label therefore reads exactly what
     * CreateMaskedEmailScreen.kt passed as `keyboardOptions`.
     */
    private fun imeActionOf(labelRes: Int): ImeAction? =
        composeRule.onNodeWithContentDescription(string(labelRes))
            .fetchSemanticsNode()
            .config
            .getOrNull(SemanticsProperties.ImeAction)

    @Test
    fun prefixFieldExposesImeActionNext() {
        assertEquals(
            "prefix field must expose ImeAction.Next",
            ImeAction.Next,
            imeActionOf(R.string.create_email_prefix_label),
        )
    }

    @Test
    fun domainFieldExposesImeActionNext() {
        assertEquals(
            "domain field must expose ImeAction.Next",
            ImeAction.Next,
            imeActionOf(R.string.create_email_domain_label),
        )
    }

    @Test
    fun descriptionFieldExposesImeActionNext() {
        assertEquals(
            "description field must expose ImeAction.Next",
            ImeAction.Next,
            imeActionOf(R.string.create_email_description_label),
        )
    }

    @Test
    fun urlFieldExposesImeActionDone() {
        assertEquals(
            "url field (final) must expose ImeAction.Done",
            ImeAction.Done,
            imeActionOf(R.string.create_email_url_label),
        )
    }

    /**
     * Drives focus only through the screen's own wiring: `requestFocus()` is
     * called once, to focus prefix (nothing focuses it on screen entry).
     * Every focus change after that comes from `performImeAction()` invoking
     * the `keyboardActions` the screen itself attached — if those callbacks
     * were removed, the following `assertIsFocused()` would find focus never
     * left the previous field and fail.
     */
    @Test
    fun imeActionChainsFocusThroughAllFourFields() {
        composeRule.onNodeWithContentDescription(string(R.string.create_email_prefix_label))
            .requestFocus()

        composeRule.onNodeWithContentDescription(string(R.string.create_email_prefix_label))
            .performImeAction()
        composeRule.onNodeWithContentDescription(string(R.string.create_email_domain_label))
            .assertIsFocused()

        composeRule.onNodeWithContentDescription(string(R.string.create_email_domain_label))
            .performImeAction()
        composeRule.onNodeWithContentDescription(string(R.string.create_email_description_label))
            .assertIsFocused()

        composeRule.onNodeWithContentDescription(string(R.string.create_email_description_label))
            .performImeAction()
        composeRule.onNodeWithContentDescription(string(R.string.create_email_url_label))
            .assertIsFocused()
    }
}
