package com.fastmask.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Text
import androidx.compose.material3.ToggleButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.fastmask.domain.model.EmailState
import com.fastmask.domain.model.MaskedEmail
import com.fastmask.ui.components.expressive.AdaptiveToggleGroup
import com.fastmask.ui.components.expressive.StateChip
import com.fastmask.ui.components.expressive.connectedToggleShapes
import com.fastmask.ui.theme.FastMaskTheme
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Device audit: at the 200% font scale the redesigned detail/create screens clipped the
 * state chip's label and broke the status toggles' labels mid-word ("Aktywn|a").
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@RunWith(AndroidJUnit4::class)
class LargeFontLayoutTest {

    @get:Rule
    val compose = createComposeRule()

    @Composable
    private fun WithFontScale(scale: Float, content: @Composable () -> Unit) {
        val density = LocalDensity.current
        CompositionLocalProvider(LocalDensity provides Density(density.density, scale)) {
            FastMaskTheme { content() }
        }
    }

    private fun mask(email: String, note: String? = null, state: EmailState = EmailState.ENABLED): MaskedEmail {
        val now = Instant.now()
        return MaskedEmail(
            id = "1", email = email, state = state, forDomain = null, description = note,
            createdBy = null, url = null, emailPrefix = null, createdAt = now, lastMessageAt = now,
        )
    }

    private fun layoutOf(node: SemanticsNode): TextLayoutResult {
        val results = mutableListOf<TextLayoutResult>()
        node.config.getOrNull(SemanticsActions.GetTextLayoutResult)!!.action!!.invoke(results)
        return results.first()
    }

    /** True when any line ends in an ellipsis, i.e. the text was cut off. */
    private fun overflowOf(node: SemanticsNode): Boolean =
        layoutOf(node).let { l -> (0 until l.lineCount).any { l.isLineEllipsized(it) } }

    @Test
    fun stateChipIsTallEnoughForItsLabelAtLargeFont() {
        compose.setContent { WithFontScale(2f) { StateChip(EmailState.ENABLED) } }

        // The chip clips what it cannot fit, so the text node itself is capped at
        // the chip height; compare what the text WANTS with what it was given.
        val label = compose.onNode(hasText("Active"), useUnmergedTree = true).fetchSemanticsNode()
        val results = mutableListOf<TextLayoutResult>()
        label.config.getOrNull(SemanticsActions.GetTextLayoutResult)!!.action!!.invoke(results)
        val layout = results.first()
        assertTrue(
            "label wants ${layout.multiParagraph.height}px but the chip gives ${layout.size.height}px",
            layout.size.height >= kotlin.math.ceil(layout.multiParagraph.height).toInt(),
        )
    }

    @Composable
    private fun TwoToggles() {
        Box(Modifier.width(360.dp)) {
            AdaptiveToggleGroup(
                first = { m, stacked ->
                    ToggleButton(
                        checked = true, onCheckedChange = {},
                        shapes = connectedToggleShapes(0, 2, vertical = stacked), modifier = m,
                    ) { Text("Aktywna") }
                },
                second = { m, stacked ->
                    ToggleButton(
                        checked = false, onCheckedChange = {},
                        shapes = connectedToggleShapes(1, 2, vertical = stacked), modifier = m,
                    ) { Text("Wyłączona") }
                },
            )
        }
    }

    @Test
    fun toggleGroupStacksAtLargeFontSoLabelsDoNotBreakMidWord() {
        compose.setContent { WithFontScale(2f) { TwoToggles() } }

        val first = compose.onNodeWithText("Aktywna").getUnclippedBoundsInRoot()
        val second = compose.onNodeWithText("Wyłączona").getUnclippedBoundsInRoot()
        assertTrue("second must sit below first", second.top >= first.bottom)
        val label = layoutOf(compose.onNode(hasText("Wyłączona"), useUnmergedTree = true).fetchSemanticsNode())
        assertEquals("label must stay on one line (no mid-word break)", 1, label.lineCount)
    }

    @Test
    fun toggleGroupStaysSideBySideAtNormalFont() {
        compose.setContent { WithFontScale(1f) { TwoToggles() } }

        val first = compose.onNodeWithText("Aktywna").getUnclippedBoundsInRoot()
        val second = compose.onNodeWithText("Wyłączona").getUnclippedBoundsInRoot()
        assertEquals(first.top, second.top)
    }
}
