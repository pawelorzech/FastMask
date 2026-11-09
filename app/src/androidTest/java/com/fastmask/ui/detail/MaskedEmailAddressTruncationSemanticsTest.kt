package com.fastmask.ui.detail

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isPopup
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.fastmask.testutil.WithFontScale
import com.fastmask.testutil.mask
import com.fastmask.testutil.overflowOf
import org.junit.Assert.assertFalse
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Regression coverage for GitHub issue #36, "Mask address truncates unreadably at large font
 * scale": at 200% the address no longer fits on one line, and an ellipsis silently ate
 * characters with no way to read them. The address (the detail screen's [Hero]) and the
 * metadata values ([ReadFields]) must wrap instead, and the address must be selectable in place.
 *
 * These assert what the user sees, the way `MaskRowLargeFontTest` does: the real composables are
 * laid out at font scale 2 in a phone-width box, and the rendered text must not be ellipsized.
 * That does not depend on a demo seed, on the emulator's screen size, or on which `Text`
 * parameters the screen happens to pass.
 */
@RunWith(AndroidJUnit4::class)
class MaskedEmailAddressTruncationSemanticsTest {

    @get:Rule
    val compose = createComposeRule()

    // Far wider than 360dp at 2x, so a one-line cap would have to cut it.
    private val longAddress = "a.very.long.local.part.that.never.fits.on.one.line@subdomain.example-company.com"
    private val longValue = "https://subdomain.example-company.com/a/long/path/that/also/never/fits/on/one/line"

    @Test
    fun maskAddressIsNotEllipsizedAtLargeFont() {
        compose.setContent {
            WithFontScale(2f) {
                Box(Modifier.width(360.dp)) { Hero(mask(longAddress)) }
            }
        }

        val node = compose.onNode(hasText(longAddress), useUnmergedTree = true).fetchSemanticsNode()
        assertFalse("the address must wrap, not lose characters to an ellipsis", overflowOf(node))
    }

    @Test
    fun metadataValuesAreNotEllipsizedAtLargeFont() {
        val email = mask(longAddress, note = longValue).copy(url = longValue, forDomain = longValue)
        compose.setContent {
            WithFontScale(2f) {
                Box(Modifier.width(360.dp)) { ReadFields(email) }
            }
        }

        // The description, domain and url rows all show the same long value.
        compose.onAllNodes(hasText(longValue), useUnmergedTree = true)
            .fetchSemanticsNodes()
            .also { check(it.isNotEmpty()) { "no metadata row showed the value" } }
            .forEach { assertFalse("a metadata value must wrap, not be ellipsized", overflowOf(it)) }
    }

    /**
     * The address must be selectable in place, not just copyable via the dedicated copy chip. A
     * plain, non-selectable `Text()` ignores a long-press entirely; inside a `SelectionContainer`
     * a long-press starts a selection, and Compose renders the selection handles as `Popup`s:
     * nodes matching `isPopup()` that were not present before the gesture.
     */
    @Test
    fun longPressingTheMaskAddressOffersTextSelection() {
        compose.setContent {
            WithFontScale(1f) { Hero(mask("quiet.harbor942@fastmail.com")) }
        }
        val popupsBefore = compose.onAllNodes(isPopup()).fetchSemanticsNodes().size

        compose.onNodeWithText("quiet.harbor942@fastmail.com").performTouchInput { longClick() }
        compose.waitForIdle()

        compose.waitUntil(timeoutMillis = 5_000) {
            compose.onAllNodes(isPopup()).fetchSemanticsNodes().size > popupsBefore
        }
    }
}
