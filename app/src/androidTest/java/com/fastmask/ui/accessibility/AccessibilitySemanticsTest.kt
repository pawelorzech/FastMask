package com.fastmask.ui.accessibility

import androidx.compose.foundation.layout.Row
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.fastmask.ui.theme.FastMaskTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AccessibilitySemanticsTest {

    @get:Rule
    val compose = createComposeRule()

    @Test
    fun screenHeadingExposesHeadingSemantics() {
        compose.setContent {
            FastMaskTheme {
                Text(text = "Screen title", modifier = Modifier.screenHeading())
            }
        }

        val heading = compose.onNodeWithText("Screen title")
            .fetchSemanticsNode()
            .config
            .getOrNull(SemanticsProperties.Heading)

        assertNotNull(heading)
    }

    @Test
    fun radioButtonGroupExposesTheGroupRelationship() {
        compose.setContent {
            FastMaskTheme {
                Row(
                    modifier = Modifier
                        .radioButtonGroup()
                        .testTag("radio-group"),
                ) {
                    Text("First")
                    Text("Second")
                }
            }
        }

        val selectableGroup = compose.onNodeWithTag("radio-group")
            .fetchSemanticsNode()
            .config
            .getOrNull(SemanticsProperties.SelectableGroup)

        assertNotNull(selectableGroup)
    }

    @Test
    fun politeLiveRegionExposesNonInterruptingAnnouncements() {
        compose.setContent {
            FastMaskTheme {
                Text(text = "Updated", modifier = Modifier.politeLiveRegion())
            }
        }

        val liveRegion = compose.onNodeWithText("Updated")
            .fetchSemanticsNode()
            .config
            .getOrNull(SemanticsProperties.LiveRegion)

        assertEquals(LiveRegionMode.Polite, liveRegion)
    }
}
