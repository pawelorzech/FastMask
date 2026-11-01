package com.fastmask.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.fastmask.domain.model.EmailState
import com.fastmask.domain.model.MaskedEmail
import com.fastmask.ui.list.MaskRow
import com.fastmask.ui.theme.FastMaskTheme
import java.time.Instant
import org.junit.Assert.assertFalse
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Device audit: at the 200% font scale (a stock Android setting) every mask address in
 * the list was reduced to "sha…" because the time label and state chip beside it were
 * measured first. The address is the row's identity and must stay readable.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@RunWith(AndroidJUnit4::class)
class MaskRowLargeFontTest {

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
    fun maskAddressIsNotEllipsizedAtLargeFont() {
        val address = "sharp.flame138@fastmask.com"
        compose.setContent {
            WithFontScale(2f) {
                Box(Modifier.width(360.dp)) {
                    MaskRow(
                        email = mask(address, note = "Quick test"),
                        index = 0, count = 1,
                        nowSec = Instant.now().epochSecond, highlighted = false,
                        onOpen = {}, onCopy = {},
                    )
                }
            }
        }

        val node = compose.onNode(hasText(address), useUnmergedTree = true).fetchSemanticsNode()
        assertFalse("the address is the row's identity and must stay readable", overflowOf(node))
    }

    @Test
    fun typicalMaskAddressIsFullyShownAtNormalFont() {
        val address = "quiet.harbor942@fastmail.com"
        compose.setContent {
            WithFontScale(1f) {
                Box(Modifier.width(411.dp)) {
                    MaskRow(
                        email = mask(address, note = "Amazon"),
                        index = 0, count = 1,
                        nowSec = Instant.now().epochSecond, highlighted = false,
                        onOpen = {}, onCopy = {},
                    )
                }
            }
        }

        val node = compose.onNode(hasText(address), useUnmergedTree = true).fetchSemanticsNode()
        assertFalse(overflowOf(node))
    }
}
