package com.fastmask.ui.list

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.fastmask.ui.theme.FastMaskTheme
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * WCAG 2.5.3 Label in Name: the extended FAB shows "New mask"; its accessible
 * name must contain that text or Voice Access ("tap New mask") cannot find it.
 */
@RunWith(AndroidJUnit4::class)
class CreateFabLabelTest {

    @get:Rule
    val compose = createComposeRule()

    @Test
    fun extendedFabAccessibleNameContainsItsVisibleLabel() {
        compose.setContent {
            FastMaskTheme {
                CreateFabMenu(
                    expanded = false, onExpandedChange = {}, extended = true,
                    isCreating = false, onNewMask = {}, onRandomMask = {},
                )
            }
        }

        // The label inside an extended FAB is cleared from the semantics tree, so the
        // button's accessible name IS its contentDescription. It must be the visible
        // label verbatim ("Nowa maska" in Polish was named "Utwórz nową maskę").
        val names = compose.onAllNodes(hasClickAction()).fetchSemanticsNodes().flatMap { node ->
            node.config.getOrNull(SemanticsProperties.ContentDescription).orEmpty()
        }
        assertTrue("accessible names $names must include the visible label", "New mask" in names)
    }
}
