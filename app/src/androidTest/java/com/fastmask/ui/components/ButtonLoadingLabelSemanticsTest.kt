package com.fastmask.ui.components

import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.fastmask.ui.theme.FastMaskTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Every action button in the app is a stock M3 `Button` whose content goes through
 * [ButtonLoadingLabel]. This pins what the label itself owns: an in-flight action is
 * never silent (the indicator carries a description), and a resting button does not
 * claim to be working. (The "is a button" role comes from the stock M3 `Button`, not
 * from our code, so it is not asserted here.)
 */
@RunWith(AndroidJUnit4::class)
class ButtonLoadingLabelSemanticsTest {

    @get:Rule
    val compose = createComposeRule()

    private fun setContent(loading: Boolean) {
        compose.setContent {
            FastMaskTheme {
                Button(onClick = {}) {
                    ButtonLoadingLabel(loading = loading, loadingDescription = WORKING) {
                        Text(LABEL)
                    }
                }
            }
        }
    }

    /** The whole point: an in-flight action must not be silent. */
    @Test
    fun aLoadingButtonDescribesItsProgress() {
        setContent(loading = true)

        compose.waitUntil(timeoutMillis = 3_000) {
            compose.onAllNodes(hasContentDescription(WORKING)).fetchSemanticsNodes().isNotEmpty()
        }
    }

    /** Mirror image: a resting button must not claim to be working. */
    @Test
    fun aRestingButtonDoesNotClaimToBeWorking() {
        setContent(loading = false)

        assertEquals(
            0,
            compose.onAllNodes(hasContentDescription(WORKING)).fetchSemanticsNodes().size,
        )
    }

    private companion object {
        const val LABEL = "Sign in"
        const val WORKING = "Working"
    }
}
