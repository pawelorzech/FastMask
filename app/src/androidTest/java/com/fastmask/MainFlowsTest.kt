package com.fastmask

import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performImeAction
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.requestFocus
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.text.input.ImeAction
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.fastmask.domain.repository.SessionRepository
import com.fastmask.testutil.enterDemoAndDismissTutorial
import com.fastmask.testutil.string
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Assert.assertEquals
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
    lateinit var sessionRepository: SessionRepository

    private lateinit var scenario: ActivityScenario<MainActivity>

    private val context = ApplicationProvider.getApplicationContext<android.content.Context>()

    /**
     * The list's create button: the extended FAB is named by its visible "New mask" label,
     * the icon-only toggle FAB (while scrolling down) by the create description.
     */
    private fun createFab(): SemanticsMatcher =
        hasClickAction() and (
            hasContentDescription(string(R.string.email_list_create_description)) or
                hasContentDescription(string(R.string.email_list_create_fab))
            )

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
     * screen. Entering the demo restores its seed list, so each test counts
     * the same masks.
     */
    private fun resetToFreshInstall() = runBlocking {
        sessionRepository.signOut()
    }

    /** Demo mode opens with coach marks over the whole list; see the shared helper for why order matters. */
    private fun enterDemoAndDismissTutorial() = composeRule.enterDemoAndDismissTutorial()

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

        // The large top app bar composes its title for both the expanded and
        // the collapsed state, so match the title without assuming one node.
        composeRule.onAllNodesWithText(string(R.string.email_list_title)).onFirst().assertExists()
    }

    // --- create -----------------------------------------------------------

    @Test
    fun creatingAMaskReturnsToTheListWithTheNewMaskOnIt() {
        enterDemoAndDismissTutorial()

        val totalBefore = filterCount(R.string.filter_all)

        // The FAB opens a menu; "New mask" is its create entry. The extended FAB
        // carries the same label until its crossfade into the toggle FAB ends,
        // so wait for the menu item to be the only match.
        composeRule.onNode(createFab()).performClick()
        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithText(string(R.string.email_list_create_fab))
                .fetchSemanticsNodes().size == 1
        }
        composeRule.onNodeWithText(string(R.string.email_list_create_fab)).performClick()
        awaitText(string(R.string.create_email_title))

        composeRule.onNodeWithContentDescription(string(R.string.create_email_description_label))
            .performTextInput("Instrumented note")
        // Scroll to submit button and click it within Compose semantics tree.
        composeRule.onNodeWithText(string(R.string.create_email_button))
            .performScrollTo()
            .performClick()

        // Wait on the FAB, which only exists on the list. ("Masked" would match
        // as a substring while still on the create screen.) The return is now
        // immediate — the create screen no longer waits out its snackbar.
        composeRule.waitUntil(timeoutMillis = 10_000) {
            composeRule.onAllNodes(createFab()).fetchSemanticsNodes().isNotEmpty()
        }
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

        val disabledBefore = filterCount(R.string.filter_disabled)
        val archivedBefore = filterCount(R.string.filter_deleted)

        // Exercise a non-default previous state: the old fallback could turn
        // an Off mask back on if id and state arrived in separate recompositions.
        composeRule.onNodeWithText(string(R.string.filter_disabled)).performClick()
        composeRule.onAllNodesWithText("@", substring = true).onFirst().performClick()
        awaitContentDescription(string(R.string.email_detail_delete))

        composeRule.onNodeWithContentDescription(string(R.string.email_detail_delete)).performScrollTo().performClick()
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

        // Undo restores the PREVIOUS state, so the mask returns to Off rather
        // than merely leaving the archive or falling back to Active.
        composeRule.waitUntil(timeoutMillis = 10_000) {
            filterCount(R.string.filter_deleted) == archivedBefore &&
                filterCount(R.string.filter_disabled) == disabledBefore
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
        composeRule.onNodeWithContentDescription(string(R.string.email_detail_delete)).performScrollTo().performClick()
        composeRule.onNodeWithText(string(R.string.email_detail_delete_confirm)).performClick()
        awaitText(string(R.string.list_archived_snackbar))

        // Let SnackbarDuration.Long produce Dismissed naturally. This covers
        // the non-action result separately from the Undo path above.
        //
        // The timeout is a delay() inside the snackbar's coroutine. When the Compose
        // test clock drives that coroutine, advancing it past the duration dismisses
        // the snackbar without waiting out the real 10 s. If this activity's
        // recomposer is not on the test clock (an ActivityScenario launch, unlike
        // setContent through the rule), the advance is a no-op and the unchanged
        // real-time wait below still decides the test: it can only get faster,
        // never weaker. autoAdvance goes back on before waiting so the exit
        // animation and the recomposition that removes the node can run.
        composeRule.mainClock.autoAdvance = false
        try {
            composeRule.mainClock.advanceTimeBy(SNACKBAR_LONG_PLUS_MARGIN_MS)
        } finally {
            composeRule.mainClock.autoAdvance = true
        }
        composeRule.waitUntil(timeoutMillis = 35_000) {
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

    private companion object {
        /** SnackbarDuration.Long is 10 s; the margin covers the dismiss animation start. */
        const val SNACKBAR_LONG_PLUS_MARGIN_MS = 11_000L
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

        val clearSearch = composeRule.onNodeWithContentDescription(
            string(R.string.email_list_clear_search),
        )
        val minimumTouchPx = 48f * context.resources.displayMetrics.density
        // The 48dp guarantee is the touch target (M3 IconButton draws a 40dp container inside it).
        assertTrue(clearSearch.fetchSemanticsNode().touchBoundsInRoot.width >= minimumTouchPx - 1f)
        clearSearch.performClick()
        composeRule.waitForIdle()

        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithText("@", substring = true)
                .fetchSemanticsNodes().size == before
        }
    }

    @Test
    fun detailEditorUsesNextAndDoneToMoveThroughTheForm() {
        enterDemoAndDismissTutorial()
        composeRule.onAllNodesWithText("@", substring = true).onFirst().performClick()
        awaitContentDescription(string(R.string.email_detail_delete))

        // The fields live behind the Details edit toggle on the expressive screen.
        composeRule.onNodeWithContentDescription(string(R.string.email_detail_edit_action))
            .performScrollTo()
            .performClick()
        awaitContentDescription(string(R.string.email_detail_description_label))

        val description = composeRule.onNodeWithContentDescription(
            string(R.string.email_detail_description_label),
        )
        val domain = composeRule.onNodeWithContentDescription(
            string(R.string.email_detail_domain_label),
        )
        val url = composeRule.onNodeWithContentDescription(
            string(R.string.email_detail_url_label),
        )

        assertEquals(
            ImeAction.Next,
            description.fetchSemanticsNode().config.getOrNull(SemanticsProperties.ImeAction),
        )
        assertEquals(
            ImeAction.Next,
            domain.fetchSemanticsNode().config.getOrNull(SemanticsProperties.ImeAction),
        )
        assertEquals(
            ImeAction.Done,
            url.fetchSemanticsNode().config.getOrNull(SemanticsProperties.ImeAction),
        )

        description.performScrollTo().requestFocus().performImeAction()
        domain.assertIsFocused().performImeAction()
        url.assertIsFocused()
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

        // The seed list contains active masks, so at least one row must carry the state
        // as a state description (the M3E rows expose it as `stateDescription` on the
        // row, next to the StateChip text) rather than only as a colour.
        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodes(
                SemanticsMatcher.expectValue(
                    SemanticsProperties.StateDescription,
                    string(R.string.state_enabled),
                ),
            ).fetchSemanticsNodes().isNotEmpty()
        }
    }
}
