package com.fastmask.ui.detail

import android.content.Context
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.isPopup
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.style.TextOverflow
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
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import javax.inject.Inject

/**
 * Regression coverage for GitHub issue #36 — "Mask address truncates
 * unreadably at large font scale".
 *
 * `MaskedEmailDetailScreen.kt:268-277` renders the mask address with
 * `maxLines = 1` + `TextOverflow.Ellipsis`, and `:413-422` (`MetaRow`) does
 * the same for the metadata values. At 200% system font scale the address no
 * longer fits on one line, so the ellipsis silently eats characters with no
 * way to expand or read them (copying still works — only *reading* is
 * broken). The fix is to let both wrap instead of truncating, and to make
 * the address selectable so it can be read and picked apart in place.
 *
 * These tests deliberately do NOT try to force a real 200% system font scale
 * via `Configuration.fontScale` + Activity recreation: that technique is
 * flaky across emulator densities/screen widths (whether a given demo
 * address actually overflows at a given scale depends on the device under
 * test). Instead they read `TextLayoutResult.layoutInput`, which reports the
 * `maxLines`/`overflow` that were actually passed to `Text()` regardless of
 * whether the current string is long enough to visibly overflow right now.
 * That is a deterministic, device-independent way to pin "this Text is
 * hard-capped at one line" versus "this Text wraps" — exactly the
 * before/after distinction this ticket is about.
 *
 * `DetailContent`/`MetaRow` are `private` inside `MaskedEmailDetailScreen.kt`
 * and therefore not directly callable from a test in another file, so these
 * drive the real, public `MaskedEmailDetailScreen` through the app's normal
 * demo-mode navigation, following the same Hilt + `ActivityScenario` pattern
 * as `MainFlowsTest`.
 */
@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
class MaskedEmailAddressTruncationSemanticsTest {

    @get:Rule(order = 0)
    val hiltRule = HiltAndroidRule(this)

    // Empty rule, not createAndroidComposeRule: see MainFlowsTest — app state
    // must be wiped BEFORE MainActivity launches and reads it in onCreate.
    @get:Rule(order = 1)
    val composeRule = createEmptyComposeRule()

    @Inject
    lateinit var settings: SettingsDataStore

    @Inject
    lateinit var tokenStorage: TokenStorage

    @Inject
    lateinit var demoSession: DemoSession

    private lateinit var scenario: ActivityScenario<MainActivity>

    private val context = ApplicationProvider.getApplicationContext<Context>()

    private fun string(id: Int) = context.getString(id)

    @Before
    fun launchOnAFreshInstall() {
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
    }

    @After
    fun tearDown() {
        if (::scenario.isInitialized) scenario.close()
    }

    /** Mirrors MainFlowsTest.enterDemoAndDismissTutorial. */
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

    /**
     * Enters demo mode and opens the first mask's detail screen, returning
     * its full address as read directly from the list row's *address* Text
     * node — never from the row as a whole.
     *
     * `MaskRow` (`MaskedEmailListScreen.kt:724-788`) wraps the row in
     * `DesignCard(onClick = ...)` (`DesignKit.kt:357-384`), which applies
     * `Modifier.clickable(role = Role.Button) { ... }`. `clickable` sets
     * `mergeDescendants = true` on the semantics it attaches, so on the
     * default (merged) semantics tree the entire row collapses into ONE
     * node whose text is the *concatenation* of the display name, the
     * relative timestamp, and the address, glued together with no
     * separator (e.g. "Quick test2mo agosharp.flame138@fastmask.com").
     * Reading that concatenation and then expecting to find it verbatim as
     * a single node on the detail screen is wrong — no such node exists
     * there, only the bare address by itself. Querying with
     * `useUnmergedTree = true` walks the raw, pre-merge tree instead, where
     * the address is still its own leaf `Text` node distinct from its
     * siblings (`MaskedEmailListScreen.kt:762-768`), so its semantics text
     * is exactly the address string and cannot accidentally include a
     * merged ancestor's other children.
     */
    private fun openFirstMaskDetail(): String {
        enterDemoAndDismissTutorial()

        val addressNode = composeRule
            .onAllNodesWithText("@", substring = true, useUnmergedTree = true)
            .onFirst()
        val fullAddress = addressNode.fetchSemanticsNode().config
            .getOrNull(SemanticsProperties.Text)
            ?.joinToString(separator = "") { it.text }
            .orEmpty()
        assertTrue("expected a mask address on the seeded demo list", fullAddress.contains("@"))

        // Click via the merged tree: the OnClick semantics action lives on
        // the row's clickable container (the merged node), not on the
        // unmerged address leaf above, and the merged node's concatenated
        // text still contains the address as a substring — so this reaches
        // the same row without depending on the concatenation's exact shape.
        composeRule.onAllNodesWithText(fullAddress, substring = true).onFirst().performClick()
        composeRule.waitUntil(timeoutMillis = 10_000) {
            composeRule.onAllNodesWithContentDescription(string(R.string.email_detail_delete))
                .fetchSemanticsNodes().isNotEmpty()
        }
        return fullAddress
    }

    /** Pulls the rendered [TextLayoutResult] out of a Text node's semantics. */
    private fun SemanticsNode.textLayoutResult(): TextLayoutResult {
        val results = mutableListOf<TextLayoutResult>()
        config.getOrNull(SemanticsActions.GetTextLayoutResult)?.action?.invoke(results)
        return results.firstOrNull()
            ?: error("node did not report a TextLayoutResult — is it a Text composable?")
    }

    /**
     * The core of the bug: the address Text at
     * `MaskedEmailDetailScreen.kt:272-273` is hard-capped with
     * `maxLines = 1` + `TextOverflow.Ellipsis`. This fails against the
     * current implementation (`maxLines == 1`, `overflow == Ellipsis`) and
     * passes once the fix removes/raises the cap and drops the ellipsis.
     */
    @Test
    fun maskAddressIsNotConstrainedToOneLineWithEllipsis() {
        val fullAddress = openFirstMaskDetail()

        // The full string must be discoverable in the semantics tree. This
        // already holds true today — Compose keeps the untruncated
        // AnnotatedString even while visually ellipsizing it — pinned here
        // as a contract the fix must not regress (e.g. by switching to a
        // manually shortened string instead of wrapping).
        composeRule.onNodeWithText(fullAddress, substring = false).assertExists()

        val layout = composeRule.onNodeWithText(fullAddress, substring = false)
            .fetchSemanticsNode()
            .textLayoutResult()

        assertTrue(
            "mask address must not be hard-capped at a single line " +
                "(MaskedEmailDetailScreen.kt:272 currently sets maxLines = 1); " +
                "actual maxLines=${layout.layoutInput.maxLines}",
            layout.layoutInput.maxLines != 1,
        )
        assertTrue(
            "mask address must not ellipsize " +
                "(MaskedEmailDetailScreen.kt:273 currently sets TextOverflow.Ellipsis); " +
                "actual overflow=${layout.layoutInput.overflow}",
            layout.layoutInput.overflow != TextOverflow.Ellipsis,
        )
    }

    /**
     * Same defect, same fix, for the metadata rows at
     * `MaskedEmailDetailScreen.kt:413-422` (`MetaRow`). "demo" is the literal
     * `MaskedEmail.createdBy` seed value rendered by the "Created by" row
     * (`DemoMaskedEmails.kt`) — it is unique on this screen (the demo-mode
     * banner text is "Demo mode — changes won't be saved", not an exact
     * match for "demo"), so it pins the metadata value Text directly without
     * needing to walk the semantics tree for a sibling node.
     */
    @Test
    fun metadataRowValueIsNotConstrainedToOneLineWithEllipsis() {
        openFirstMaskDetail()

        val layout = composeRule.onNodeWithText("demo", substring = false)
            .fetchSemanticsNode()
            .textLayoutResult()

        assertTrue(
            "metadata row value must not be hard-capped at a single line " +
                "(MaskedEmailDetailScreen.kt:418 currently sets maxLines = 1); " +
                "actual maxLines=${layout.layoutInput.maxLines}",
            layout.layoutInput.maxLines != 1,
        )
        assertTrue(
            "metadata row value must not ellipsize " +
                "(MaskedEmailDetailScreen.kt:419 currently sets TextOverflow.Ellipsis); " +
                "actual overflow=${layout.layoutInput.overflow}",
            layout.layoutInput.overflow != TextOverflow.Ellipsis,
        )
    }

    /**
     * The address must be selectable in place, not just copyable via the
     * dedicated copy chip. A plain, non-selectable `Text()` — what ships
     * today — ignores a long-press entirely. Once the fix wraps it in a
     * `SelectionContainer`, a long-press starts a text selection, and
     * Compose renders the selection handles as `Popup`s — new nodes
     * matching `isPopup()` that were not present before the gesture.
     */
    @Test
    fun longPressingTheMaskAddressOffersTextSelection() {
        val fullAddress = openFirstMaskDetail()

        val popupsBefore = composeRule.onAllNodes(isPopup()).fetchSemanticsNodes().size

        composeRule.onNodeWithText(fullAddress, substring = false)
            .performTouchInput { longClick() }
        composeRule.waitForIdle()

        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodes(isPopup()).fetchSemanticsNodes().size > popupsBefore
        }
    }
}
