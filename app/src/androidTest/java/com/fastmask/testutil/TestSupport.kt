package com.fastmask.testutil

import android.content.Context
import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.junit4.ComposeTestRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import androidx.test.core.app.ApplicationProvider
import com.fastmask.R
import com.fastmask.domain.model.EmailState
import com.fastmask.domain.model.MaskedEmail
import com.fastmask.ui.theme.FastMaskTheme
import java.time.Instant

/**
 * Helpers shared by the instrumented tests. They used to be copy-pasted into each
 * test class, which let the copies drift (one comment even read "mirrors
 * MainFlowsTest").
 */

/** A string from the app under test, in the device's current locale. */
fun string(@StringRes id: Int): String =
    ApplicationProvider.getApplicationContext<Context>().getString(id)

/** The theme at a given font scale, the way a user's system setting reaches the UI. */
@Composable
fun WithFontScale(scale: Float, content: @Composable () -> Unit) {
    val density = LocalDensity.current
    CompositionLocalProvider(LocalDensity provides Density(density.density, scale)) {
        FastMaskTheme { content() }
    }
}

/** Semantics matchers for the accessibility properties the screens promise. */
val isHeadingNode: SemanticsMatcher = SemanticsMatcher.keyIsDefined(SemanticsProperties.Heading)

val isPoliteLiveRegion: SemanticsMatcher =
    SemanticsMatcher.expectValue(SemanticsProperties.LiveRegion, LiveRegionMode.Polite)

val isSelectableGroup: SemanticsMatcher =
    SemanticsMatcher.keyIsDefined(SemanticsProperties.SelectableGroup)

/** A mask with fixed, uninteresting fields around the ones a test cares about. */
fun mask(email: String, note: String? = null, state: EmailState = EmailState.ENABLED): MaskedEmail {
    val now = Instant.now()
    return MaskedEmail(
        id = "1", email = email, state = state, forDomain = null, description = note,
        createdBy = null, url = null, emailPrefix = null, createdAt = now, lastMessageAt = now,
    )
}

/** The laid-out text of a `Text` node. */
fun layoutOf(node: SemanticsNode): TextLayoutResult {
    val results = mutableListOf<TextLayoutResult>()
    node.config.getOrNull(SemanticsActions.GetTextLayoutResult)!!.action!!.invoke(results)
    return results.first()
}

/** True when any line ends in an ellipsis, i.e. the text was cut off. */
fun overflowOf(node: SemanticsNode): Boolean =
    layoutOf(node).let { l -> (0 until l.lineCount).any { l.isLineEllipsized(it) } }

/**
 * From the Welcome screen: opens the demo and gets past its coach marks.
 *
 * Waits for the tutorial FIRST, then for the list. It used to wait for the list
 * title and dismiss the tutorial afterwards, which stopped working the moment the
 * overlay became modal for accessibility (audit 2026-07-27): the content beneath
 * is dropped from the semantics tree while the coach marks are up, so the title
 * genuinely is not there to find, which is what a screen-reader user experiences.
 */
fun ComposeTestRule.enterDemoAndDismissTutorial() {
    onNodeWithText(string(R.string.welcome_demo_cta)).performClick()
    waitUntil(timeoutMillis = 10_000) {
        onAllNodesWithText(string(R.string.tutorial_skip)).fetchSemanticsNodes().isNotEmpty() ||
            onAllNodesWithText(string(R.string.email_list_title)).fetchSemanticsNodes().isNotEmpty()
    }
    val skip = onAllNodesWithText(string(R.string.tutorial_skip))
    if (skip.fetchSemanticsNodes().isNotEmpty()) skip.onFirst().performClick()
    waitUntil(timeoutMillis = 10_000) {
        onAllNodesWithText(string(R.string.email_list_title)).fetchSemanticsNodes().isNotEmpty()
    }
    waitForIdle()
}

/**
 * Re-checks [condition] until it yields a non-null value, for things that happen in
 * another process (the notification manager) where there is no Compose idle to wait
 * on. Returns as soon as the value exists; only a miss costs a poll interval.
 */
fun <T : Any> awaitNonNull(
    what: String,
    timeoutMillis: Long = 10_000,
    pollMillis: Long = 25,
    condition: () -> T?,
): T {
    val deadline = System.nanoTime() + timeoutMillis * 1_000_000
    while (true) {
        condition()?.let { return it }
        if (System.nanoTime() >= deadline) throw AssertionError("timed out after ${timeoutMillis}ms: $what")
        Thread.sleep(pollMillis)
    }
}
