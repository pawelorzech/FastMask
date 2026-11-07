package com.fastmask.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.fastmask.testutil.WithFontScale
import com.fastmask.testutil.mask
import com.fastmask.testutil.overflowOf
import com.fastmask.ui.list.MaskRow
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
