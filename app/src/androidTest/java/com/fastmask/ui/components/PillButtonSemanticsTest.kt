package com.fastmask.ui.components

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.fastmask.ui.theme.FastMaskTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * `PillButton` is the one button in the app: sign in, create, save, enable /
 * disable, retry, both dialog buttons, buy, restore, unlock. Its accessibility
 * semantics are therefore worth pinning directly rather than through whichever
 * screen happens to exercise them.
 *
 * These exist because the audit of 2026-08-01 found the previous pass's
 * screen-reader work was unreachable: the progress announcement required BOTH
 * `loading` and `loadingDescription`, and no call site in the app set both. The
 * four screens that passed a description signalled progress with
 * `enabled = !isLoading` plus a spinner of their own, so `loading` stayed false;
 * the two that set `loading` passed no description. A translated string in 20
 * locales served a branch that could not execute, and nothing failed.
 */
@RunWith(AndroidJUnit4::class)
class PillButtonSemanticsTest {

    @get:Rule
    val compose = createComposeRule()

    private fun setContent(loading: Boolean) {
        compose.setContent {
            FastMaskTheme {
                PillButton(text = LABEL, onClick = {}, loading = loading)
            }
        }
    }

    @Test
    fun theButtonAnnouncesItselfAsAButton() {
        setContent(loading = false)

        val role = compose.onNodeWithText(LABEL)
            .fetchSemanticsNode()
            .config
            .getOrNull(SemanticsProperties.Role)

        assertEquals(Role.Button, role)
    }

    /** The whole point: an in-flight action must not be silent. */
    @Test
    fun aLoadingButtonCarriesAStateDescription() {
        setContent(loading = true)

        val stateDescription = compose.onNodeWithText(LABEL)
            .fetchSemanticsNode()
            .config
            .getOrNull(SemanticsProperties.StateDescription)

        assertEquals(
            "a loading button must describe its state to a screen reader",
            true,
            !stateDescription.isNullOrBlank(),
        )
    }

    /**
     * The mirror image: a resting button must not claim to be working. Without
     * this the previous assertion would pass against a component that announces
     * unconditionally.
     */
    @Test
    fun aRestingButtonHasNoStateDescription() {
        setContent(loading = false)

        val stateDescription = compose.onNodeWithText(LABEL)
            .fetchSemanticsNode()
            .config
            .getOrNull(SemanticsProperties.StateDescription)

        assertNull(stateDescription)
    }

    private companion object {
        const val LABEL = "Sign in"
    }
}
