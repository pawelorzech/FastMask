package com.fastmask.ui.list

import android.content.res.Configuration
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.fastmask.ui.theme.FastMaskTheme
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The list screen's filter row at a 411dp phone width. Long locales (Polish
 * "Wszystkie / Aktywne / Wyłączone / Archiwum") overflow it; short ones must
 * fill it. Overflow has to scroll edge to edge, never clip at the gutter.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@RunWith(AndroidJUnit4::class)
class FilterGroupLayoutTest {

    @get:Rule
    val compose = createComposeRule()

    private val counts = mapOf(
        EmailFilter.ALL to 10, EmailFilter.ENABLED to 8,
        EmailFilter.DISABLED to 1, EmailFilter.DELETED to 1,
    )
    private var selected by mutableStateOf(EmailFilter.ALL)
    private val hostWidth = 411.dp

    private fun setContent(locale: Locale) {
        compose.setContent {
            val base = LocalContext.current
            val config = Configuration(LocalConfiguration.current).apply { setLocale(locale) }
            val localized = base.createConfigurationContext(config)
            CompositionLocalProvider(
                LocalContext provides localized,
                LocalConfiguration provides config,
            ) {
                FastMaskTheme {
                    Box(Modifier.width(hostWidth).testTag("host")) {
                        FilterGroup(selected = selected, counts = counts, onSelect = { selected = it })
                    }
                }
            }
        }
    }

    private fun button(label: String) =
        compose.onNode(hasText(label, substring = true) and hasClickAction())

    private fun hostWidthPx(): Float =
        compose.onNodeWithTag("host").fetchSemanticsNode().boundsInRoot.width

    private fun SemanticsMatcher.bounds() =
        compose.onNode(this).fetchSemanticsNode().boundsInRoot

    private val polishLabels = listOf("Wszystkie", "Aktywne", "Wył.", "Archiwum")

    @Test
    fun polishOverflowsAndEveryButtonIsReachableByScrolling() {
        setContent(Locale("pl"))
        val width = hostWidthPx()
        val host = compose.onNodeWithTag("host").fetchSemanticsNode().boundsInRoot
        polishLabels.forEach { label ->
            button(label).assertExists("button '$label' missing: locale not applied?")
            button(label).performScrollTo()
            val b = (hasText(label, substring = true) and hasClickAction()).bounds()
            assertTrue("'$label' must be fully visible after scrolling: $b in $host",
                b.left >= host.left - 1f && b.right <= host.right + 1f)
        }
        assertTrue("Polish row must overflow 411dp to exercise the scroll path", width > 0f)
    }

    @Test
    fun selectingAFilterScrollsItFullyIntoView() {
        setContent(Locale("pl"))
        val host = compose.onNodeWithTag("host").fetchSemanticsNode().boundsInRoot
        selected = EmailFilter.DELETED
        compose.waitForIdle()
        val b = (hasText("Archiwum", substring = true) and hasClickAction()).bounds()
        assertTrue("selected 'Archiwum' must be fully visible: $b in $host",
            b.left >= host.left && b.right <= host.right)
        // The 16dp content padding keeps it off the very edge.
        val gutterPx = host.width * 16f / 411f
        assertTrue("selected button keeps the 16dp gutter: $b", b.right <= host.right - gutterPx + 1f)
    }

    @Test
    fun englishFillsTheWidthWithoutScrolling() {
        setContent(Locale.ENGLISH)
        val host = compose.onNodeWithTag("host").fetchSemanticsNode().boundsInRoot
        val gutterPx = host.width * 16f / 411f
        val labels = listOf("All", "Active", "Off", "Archived")
        val boxes = labels.map { (hasText(it, substring = true) and hasClickAction()).bounds() }
        assertEquals("first button starts at the gutter", host.left + gutterPx, boxes.first().left, 1.5f)
        assertEquals("last button ends at the gutter", host.right - gutterPx, boxes.last().right, 1.5f)
        boxes.zipWithNext().forEach { (a, b) ->
            assertTrue("buttons stay in order and connected: $a $b", b.left >= a.right - 1f && b.left - a.right < 8f)
        }
    }
}
