package com.fastmask

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.test.espresso.Espresso
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.fastmask.data.local.SettingsDataStore
import com.fastmask.data.local.TokenStorage
import com.fastmask.domain.model.AppMode
import com.fastmask.domain.repository.DemoSession
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import javax.inject.Inject
import kotlinx.coroutines.runBlocking

/**
 * End-to-end coverage of the paths a user actually walks, driven through the
 * real Activity, the real navigation graph and the real Hilt graph.
 *
 * Everything runs in **demo mode**, which is backed by the in-memory
 * repository — no Fastmail token, no network, and the seed list is identical on
 * every run. That makes these the only tests that would have caught the class
 * of defect the four audit passes kept finding by hand: dead taps, wrong-locale
 * text, and navigation that silently goes nowhere.
 */
@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
class MainFlowsTest {

    @get:Rule(order = 0)
    val hiltRule = HiltAndroidRule(this)

    // Empty rule, not createAndroidComposeRule: the Activity reads persisted
    // state in onCreate to pick its start destination, so app state has to be
    // wiped BEFORE it launches, not after.
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
    fun launchOnAFreshInstall() {
        hiltRule.inject()
        resetToFreshInstall()
        scenario = ActivityScenario.launch(MainActivity::class.java)
        composeRule.waitUntil(timeoutMillis = 10_000) {
            composeRule.onAllNodesWithText(string(R.string.welcome_demo_cta))
                .fetchSemanticsNodes().isNotEmpty()
        }
    }

    @After
    fun tearDown() {
        if (::scenario.isInitialized) scenario.close()
    }

    /**
     * Puts the app back to a just-installed state through its own API.
     *
     * Deleting the DataStore files instead does not work: DataStore is a
     * process-wide singleton that keeps serving its in-memory value, so the
     * second test would still start in demo mode and never see the welcome
     * screen. The demo repository is a singleton too — [DemoSession.reset]
     * gives each test the same seed list to count.
     */
    private fun resetToFreshInstall() = runBlocking {
        tokenStorage.clearToken()
        settings.setAppMode(AppMode.REAL)
        settings.setTutorialCompleted(false)
        demoSession.reset()
    }

    /**
     * Demo mode opens with coach marks over the whole list; get past them.
     *
     * Waits for the tutorial FIRST, then for the list. It used to wait for the
     * list title and dismiss the tutorial afterwards, which stopped working the
     * moment the overlay became modal for accessibility (audit 2026-07-27): the
     * content beneath is dropped from the semantics tree while the coach marks
     * are up, so the title genuinely is not there to find — which is the point
     * of the change, and what a screen-reader user now experiences.
     */
    private fun enterDemoAndDismissTutorial() {
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
        composeRule.waitForIdle()
    }

    private fun awaitContentDescription(value: String, timeoutMillis: Long = 10_000) {
        composeRule.waitUntil(timeoutMillis) {
            composeRule.onAllNodesWithContentDescription(value)
                .fetchSemanticsNodes().isNotEmpty()
        }
    }

    private fun awaitText(text: String, timeoutMillis: Long = 10_000) {
        composeRule.waitUntil(timeoutMillis) {
            composeRule.onAllNodesWithText(text, substring = true)
                .fetchSemanticsNodes().isNotEmpty()
        }
    }

    // --- entry ------------------------------------------------------------

    @Test
    fun aFreshInstallLandsOnWelcomeAndDemoOpensTheList() {
        composeRule.onNodeWithText(string(R.string.welcome_signin_cta)).assertIsDisplayed()

        enterDemoAndDismissTutorial()

        composeRule.onNodeWithText(string(R.string.email_list_title)).assertIsDisplayed()
    }

    // --- create -----------------------------------------------------------

    @Test
    fun creatingAMaskReturnsToTheListWithTheNewMaskOnIt() {
        enterDemoAndDismissTutorial()

        val totalBefore = filterCount(R.string.filter_all)

        composeRule
            .onNodeWithContentDescription(string(R.string.email_list_create_description))
            .performClick()
        awaitText(string(R.string.create_email_title))

        composeRule.onNodeWithContentDescription(string(R.string.create_email_description_label))
            .performTextInput("Instrumented note")
        // The soft keyboard opens on input and pushes the submit button out of
        // the viewport; clicking without scrolling to it silently misses and
        // lands on the back affordance instead, so the test "passes" back to
        // the list having created nothing.
        Espresso.closeSoftKeyboard()
        composeRule.onNodeWithText(string(R.string.create_email_button))
            .performScrollTo()
            .performClick()

        // Wait on the FAB, which only exists on the list. ("Masked" would match
        // as a substring while still on the create screen.) The return is now
        // immediate — the create screen no longer waits out its snackbar.
        awaitContentDescription(string(R.string.email_list_create_description), 10_000)
        composeRule.waitUntil(timeoutMillis = 15_000) {
            filterCount(R.string.filter_all) == totalBefore + 1
        }

        // The confirmation now lands on the list, naming the new address.
        awaitText("@")
    }

    // --- archive + undo ---------------------------------------------------

    /**
     * The highest-risk path in the app: it destroys user data and offers a
     * time-limited undo that must restore the PREVIOUS state, not a default.
     */
    @Test
    fun archivingAMaskOffersAnUndoThatPutsItBack() {
        enterDemoAndDismissTutorial()

        val activeBefore = filterCount(R.string.filter_enabled)
        val archivedBefore = filterCount(R.string.filter_deleted)

        composeRule.onAllNodesWithText("@", substring = true).onFirst().performClick()
        awaitContentDescription(string(R.string.email_detail_delete))

        composeRule.onNodeWithContentDescription(string(R.string.email_detail_delete)).performClick()
        composeRule.onNodeWithText(string(R.string.email_detail_delete_confirm)).performClick()

        // Back on the list. The default "All" filter still shows archived
        // masks, so the row count is unchanged — the filter chips are what
        // moves, and they are also what the user reads.
        awaitText(string(R.string.list_archived_snackbar))
        composeRule.waitUntil(timeoutMillis = 5_000) {
            filterCount(R.string.filter_deleted) == archivedBefore + 1
        }

        // Configuration change used to clear the SavedStateHandle as soon as
        // the snackbar appeared, so Activity recreation permanently lost Undo.
        scenario.recreate()
        awaitText(string(R.string.list_archived_snackbar))

        composeRule.onNodeWithText(string(R.string.list_undo)).performClick()

        // Undo restores the PREVIOUS state, so the mask returns to Active
        // rather than merely leaving the archive.
        composeRule.waitUntil(timeoutMillis = 10_000) {
            filterCount(R.string.filter_deleted) == archivedBefore &&
                filterCount(R.string.filter_enabled) == activeBefore
        }

        // Once the snackbar has produced a result, its navigation state is
        // consumed. A later recreation must not resurrect a stale Undo.
        scenario.recreate()
        awaitText(string(R.string.email_list_title))
        composeRule.waitForIdle()
        assertTrue(
            composeRule.onAllNodesWithText(string(R.string.list_archived_snackbar))
                .fetchSemanticsNodes().isEmpty(),
        )
    }

    @Test
    fun dismissedArchiveUndoDoesNotReturnAfterRecreation() {
        enterDemoAndDismissTutorial()

        composeRule.onAllNodesWithText("@", substring = true).onFirst().performClick()
        awaitContentDescription(string(R.string.email_detail_delete))
        composeRule.onNodeWithContentDescription(string(R.string.email_detail_delete)).performClick()
        composeRule.onNodeWithText(string(R.string.email_detail_delete_confirm)).performClick()
        awaitText(string(R.string.list_archived_snackbar))

        // Let SnackbarDuration.Long produce Dismissed naturally. This covers
        // the non-action result separately from the Undo path above.
        composeRule.waitUntil(timeoutMillis = 20_000) {
            composeRule.onAllNodesWithText(string(R.string.list_archived_snackbar))
                .fetchSemanticsNodes().isEmpty()
        }

        scenario.recreate()
        awaitText(string(R.string.email_list_title))
        composeRule.waitForIdle()
        assertTrue(
            composeRule.onAllNodesWithText(string(R.string.list_archived_snackbar))
                .fetchSemanticsNodes().isEmpty(),
        )
    }

    /** Reads the number a filter chip shows, e.g. "Active" -> 8. */
    private fun filterCount(labelRes: Int): Int {
        val label = string(labelRes)
        val node = composeRule.onAllNodesWithText(label, substring = true)
            .fetchSemanticsNodes()
            .firstOrNull { n ->
                n.config.getOrNull(SemanticsProperties.Text)?.any { it.text == label } == true
            } ?: return -1
        return node.config.getOrNull(SemanticsProperties.Text)
            ?.mapNotNull { it.text.toIntOrNull() }
            ?.firstOrNull() ?: -1
    }

    // --- search -----------------------------------------------------------

    @Test
    fun searchNarrowsTheListAndClearingItRestoresEverything() {
        enterDemoAndDismissTutorial()

        val before = composeRule.onAllNodesWithText("@", substring = true)
            .fetchSemanticsNodes().size

        composeRule.onNodeWithContentDescription(
            string(R.string.email_list_search_placeholder)
        ).performTextInput("zzzz-no-such-mask")
        composeRule.waitForIdle()

        composeRule.onNodeWithText(string(R.string.email_list_no_matches)).assertIsDisplayed()

        composeRule.onNodeWithContentDescription(string(R.string.email_list_clear_search))
            .performClick()
        composeRule.waitForIdle()

        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithText("@", substring = true)
                .fetchSemanticsNodes().size == before
        }
    }

    // --- settings ---------------------------------------------------------

    @Test
    fun settingsOpensFromTheList() {
        enterDemoAndDismissTutorial()

        composeRule.onNodeWithContentDescription(string(R.string.email_list_settings)).performClick()

        awaitText(string(R.string.settings_title))
    }

    // --- accessibility ----------------------------------------------------

    /**
     * A mask's state used to be conveyed by the colour of a dot alone, which
     * TalkBack cannot announce and a colour-blind user cannot distinguish.
     */
    @Test
    fun maskStateIsExposedToScreenReadersNotJustAsColour() {
        enterDemoAndDismissTutorial()

        composeRule.onAllNodesWithText("@", substring = true).onFirst().assertIsDisplayed()

        // The seed list contains active masks, so at least one row must carry
        // the state as a content description rather than only as a colour.
        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodes(hasContentDescription(string(R.string.state_enabled)))
                .fetchSemanticsNodes().isNotEmpty()
        }
    }
}
