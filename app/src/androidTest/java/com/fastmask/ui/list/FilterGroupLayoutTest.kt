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
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.fastmask.ui.theme.FastMaskTheme
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The list screen's filter row at a 411dp phone width.
 *
 * - English fits with its counts and fills the width gutter to gutter.
 * - Polish ("Wszystkie / Aktywne / Wył. / Archiwum") does not fit with counts; the
 *   counts are dropped (the app bar subtitle states them) and all four buttons must
 *   be fully on screen, with nothing to scroll. 1.13.0 left "Archiwum" cut off.
 * - At 200% font even the plain labels overflow; then, and only then, the row
 *   scrolls edge to edge and the selected filter scrolls fully into view.
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
    private val polishLabels = listOf("Wszystkie", "Aktywne", "Wył.", "Archiwum")
    private val englishLabels = listOf("All", "Active", "Off", "Archived")

    private fun setContent(locale: Locale, fontScale: Float = 1f) {
        compose.setContent {
            val base = LocalContext.current
            val config = Configuration(LocalConfiguration.current).apply { setLocale(locale) }
            val localized = base.createConfigurationContext(config)
            val density = LocalDensity.current
            CompositionLocalProvider(
                LocalContext provides localized,
                LocalConfiguration provides config,
                LocalDensity provides Density(density.density, fontScale),
            ) {
                FastMaskTheme {
                    Box(Modifier.width(hostWidth).testTag("host")) {
                        FilterGroup(selected = selected, counts = counts, onSelect = { selected = it })
                    }
                }
            }
        }
    }

    private fun button(label: String): SemanticsMatcher = hasText(label, substring = true) and hasClickAction()

    private fun SemanticsMatcher.bounds() = compose.onNode(this).fetchSemanticsNode().boundsInRoot

    /** Texts merged into a button, e.g. ["Archiwum", "1"] with counts, ["Archiwum"] without. */
    private fun SemanticsMatcher.texts(): List<String> =
        compose.onNode(this).fetchSemanticsNode().config
            .getOrNull(SemanticsProperties.Text).orEmpty().map { it.text }

    private fun host() = compose.onNodeWithTag("host").fetchSemanticsNode().boundsInRoot

    @Test
    fun polishFitsByDroppingCountsAndNothingIsCutOff() {
        setContent(Locale("pl"))
        val host = host()
        val gutterPx = host.width * 16f / 411f
        polishLabels.forEach { label ->
            val b = button(label).bounds()
            assertTrue(
                "'$label' must be fully on screen without scrolling: $b in $host",
                b.left >= host.left + gutterPx - 1f && b.right <= host.right - gutterPx + 1f,
            )
            assertFalse("'$label' drops its count to fit: ${button(label).texts()}",
                button(label).texts().any { it.all(Char::isDigit) })
        }
    }

    @Test
    fun englishFillsTheWidthAndKeepsItsCounts() {
        setContent(Locale.ENGLISH)
        val host = host()
        val gutterPx = host.width * 16f / 411f
        val boxes = englishLabels.map { button(it).bounds() }
        assertEquals("first button starts at the gutter", host.left + gutterPx, boxes.first().left, 1.5f)
        assertEquals("last button ends at the gutter", host.right - gutterPx, boxes.last().right, 1.5f)
        boxes.zipWithNext().forEach { (a, b) ->
            assertTrue("buttons stay in order and connected: $a $b", b.left >= a.right - 1f && b.left - a.right < 8f)
        }
        assertTrue("English keeps the counts: ${button("Archived").texts()}", "1" in button("Archived").texts())
    }

    @Test
    fun atLargeFontTheRowScrollsAndEveryButtonIsReachable() {
        setContent(Locale("pl"), fontScale = 2f)
        val host = host()
        polishLabels.forEach { label ->
            compose.onNode(button(label)).performScrollTo()
            val b = button(label).bounds()
            assertTrue("'$label' must be fully visible after scrolling: $b in $host",
                b.left >= host.left - 1f && b.right <= host.right + 1f)
        }
    }

    @Test
    fun atLargeFontSelectingAFilterScrollsItFullyIntoView() {
        setContent(Locale("pl"), fontScale = 2f)
        val host = host()
        selected = EmailFilter.DELETED
        compose.waitForIdle()
        val b = button("Archiwum").bounds()
        assertTrue("selected 'Archiwum' must be fully visible: $b in $host",
            b.left >= host.left && b.right <= host.right)
        val gutterPx = host.width * 16f / 411f
        assertTrue("selected button keeps the 16dp gutter: $b", b.right <= host.right - gutterPx + 1f)
    }
}
