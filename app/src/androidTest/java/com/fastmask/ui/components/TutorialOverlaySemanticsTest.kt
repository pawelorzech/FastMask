package com.fastmask.ui.components

import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.fastmask.ui.theme.FastMaskTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * GitHub issue #39 — the tutorial overlay is a role-less "Skip" and a silent
 * step transition: `AnimatedContent` swaps the title/body Text nodes with no
 * live region and no focus move, so TalkBack never announces that anything
 * changed when the user taps "Next". These pin the two accessibility fixes
 * the issue calls "worth doing either way" (role-less Skip, silent step
 * change) — NOT the deferred Back button / step counter.
 */
@RunWith(AndroidJUnit4::class)
class TutorialOverlaySemanticsTest {

    @get:Rule
    val compose = createComposeRule()

    private fun setContent(steps: List<TutorialStep>) {
        compose.setContent {
            FastMaskTheme {
                TutorialOverlay(
                    steps = steps,
                    visible = true,
                    onComplete = {},
                    onSkip = {},
                )
            }
        }
    }

    @Test
    fun skipExposesButtonRole() {
        setContent(listOf(STEP_ONE))

        val role = compose.onNodeWithText("Skip")
            .fetchSemanticsNode()
            .config
            .getOrNull(SemanticsProperties.Role)

        assertEquals(
            "the Skip control is a clickable Text with no declared role today; " +
                "a screen reader announces it as plain text instead of a button",
            Role.Button,
            role,
        )
    }

    /**
     * The step container must carry a polite live region so that swapping
     * from one step's title/body to the next is announced. Walk up from the
     * title Text node looking for it, rather than pinning to one exact
     * composable in the tree, since the fix owns exactly where in the
     * hierarchy the live region gets attached.
     */
    @Test
    fun stepContainerExposesPoliteLiveRegionAroundTitleAndBody() {
        setContent(listOf(STEP_ONE, STEP_TWO))

        val titleNode = compose.onNodeWithText(STEP_ONE.title, useUnmergedTree = true)
            .fetchSemanticsNode()

        val liveRegion = titleNode.nearestAncestorLiveRegion()

        assertEquals(
            "no ancestor of the step title declares a live region today, so " +
                "TalkBack stays silent when Next swaps to the following step",
            LiveRegionMode.Polite,
            liveRegion,
        )
    }

    /**
     * A polite live region on the wrong ancestor is worse than none: GitHub
     * issue #39's first fix pass put `politeLiveRegion()` on the shared
     * `AnimatedContent` container. That container hosts BOTH the outgoing and
     * incoming `TooltipBubble` for the full 300ms crossfade, so the moment
     * "Next" is tapped, the live region's subtree contains both steps' title
     * and body at once — TalkBack is liable to read the old step and the new
     * step concatenated, then announce the new step again once the old one
     * finally leaves composition. This pins the real requirement: mid-
     * crossfade, the live region's announced text must be the incoming
     * step's alone.
     */
    @Test
    fun stepContainerLiveRegionScopesToTheEnteringStepOnly() {
        compose.mainClock.autoAdvance = false
        setContent(listOf(STEP_ONE, STEP_TWO))
        compose.waitForIdle()

        compose.onNodeWithText(STEP_ONE.title).assertExists()
        compose.onNodeWithText("Next").performClick()

        // Advance partway into the 300ms crossfade (`ANIM_DURATION_MS` in
        // TutorialOverlay.kt) — short of letting it finish, which is exactly
        // when `AnimatedContent` still has both the outgoing step-1 bubble
        // and the incoming step-2 bubble mounted simultaneously. That
        // overlap window is when a live region scoped to the wrong ancestor
        // leaks the outgoing step's text into the announcement.
        compose.mainClock.advanceTimeBy(150L)
        compose.waitForIdle()

        val incomingTitleNode = compose.onNodeWithText(STEP_TWO.title, useUnmergedTree = true)
            .fetchSemanticsNode()

        val liveRegionNode = checkNotNull(incomingTitleNode.nearestAncestorLiveRegionNode()) {
            "no ancestor of the incoming step's title declares a live region " +
                "mid-crossfade, so TalkBack has nothing to announce"
        }

        val announced = liveRegionNode.collectDescendantText()

        assertTrue(
            "the live region's subtree should contain the incoming step's own title/body",
            announced.any { it.contains(STEP_TWO.title) } &&
                announced.any { it.contains(STEP_TWO.description) },
        )
        assertFalse(
            "the live region is scoped too broadly and still contains the outgoing " +
                "step's title mid-crossfade — TalkBack would read the old and new " +
                "step's text run together instead of announcing only the new step",
            announced.any { it.contains(STEP_ONE.title) },
        )
        assertFalse(
            "the live region is scoped too broadly and still contains the outgoing " +
                "step's body mid-crossfade — TalkBack would read the old and new " +
                "step's text run together instead of announcing only the new step",
            announced.any { it.contains(STEP_ONE.description) },
        )

        compose.mainClock.autoAdvance = true
    }

    private fun SemanticsNode.nearestAncestorLiveRegion(): LiveRegionMode? =
        nearestAncestorLiveRegionNode()?.config?.getOrNull(SemanticsProperties.LiveRegion)

    private fun SemanticsNode.nearestAncestorLiveRegionNode(): SemanticsNode? {
        var node: SemanticsNode? = this
        while (node != null) {
            val region = node.config.getOrNull(SemanticsProperties.LiveRegion)
            if (region != null) return node
            node = node.parent
        }
        return null
    }

    /** All text carried by this node and every node beneath it, unmerged. */
    private fun SemanticsNode.collectDescendantText(): List<String> {
        val texts = mutableListOf<String>()
        config.getOrNull(SemanticsProperties.Text)?.forEach { texts += it.text }
        children.forEach { texts += it.collectDescendantText() }
        return texts
    }

    private companion object {
        val STEP_ONE = TutorialStep(
            title = "Create your first mask",
            description = "Tap the plus button to generate a masked email.",
            targetBounds = null as Rect?,
        )
        val STEP_TWO = TutorialStep(
            title = "Manage your masks",
            description = "Tap any mask to view or disable it.",
            targetBounds = null as Rect?,
        )
    }
}
