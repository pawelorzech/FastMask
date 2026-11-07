package com.fastmask.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Text
import androidx.compose.material3.ToggleButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.fastmask.domain.model.EmailState
import com.fastmask.testutil.WithFontScale
import com.fastmask.testutil.layoutOf
import com.fastmask.ui.components.expressive.AdaptiveToggleGroup
import com.fastmask.ui.components.expressive.StateChip
import com.fastmask.ui.components.expressive.connectedToggleShapes
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

    @Test
    fun stateChipIsTallEnoughForItsLabelAtLargeFont() {
        compose.setContent { WithFontScale(2f) { StateChip(EmailState.ENABLED) } }

        // The chip clips what it cannot fit, so the text node itself is capped at
        // the chip height; compare what the text WANTS with what it was given.
        val label = compose.onNode(hasText("Active"), useUnmergedTree = true).fetchSemanticsNode()
        val layout = layoutOf(label)
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
