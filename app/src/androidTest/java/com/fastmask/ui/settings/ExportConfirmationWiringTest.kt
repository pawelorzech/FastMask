package com.fastmask.ui.settings

import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.remember
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.fastmask.R
import com.fastmask.testutil.string
import com.fastmask.ui.theme.FastMaskTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The wiring around the CSV export: the settings row only REQUESTS an export, the
 * confirmation dialog appears when the state says so, and its two buttons go to
 * different callbacks (confirm exports, cancel only closes).
 *
 * Replaces the source-grep `ExportConfirmationWiringTest` (unit). The dialog on its
 * own is covered by [ExportConfirmationDialogTest]; this renders the real
 * [SettingsContent] so the row -> dialog connection is exercised.
 */
@RunWith(AndroidJUnit4::class)
class ExportConfirmationWiringTest {

    @get:Rule
    val compose = createComposeRule()

    private var requested = 0
    private var confirmed = 0
    private var dismissed = 0

    private fun show(showConfirmation: Boolean) {
        compose.setContent {
            FastMaskTheme {
                SettingsContent(
                    state = SettingsViewState(
                        isPro = true,
                        monetizationEnabled = true,
                        showExportConfirmation = showConfirmation,
                    ),
                    snackbarHostState = remember { SnackbarHostState() },
                    callbacks = SettingsCallbacks(
                        onExportClick = { requested += 1 },
                        onExportConfirmed = { confirmed += 1 },
                        onExportDismissed = { dismissed += 1 },
                    ),
                )
            }
        }
    }

    @Test
    fun tappingTheExportRowOnlyRequestsAnExport() {
        show(showConfirmation = false)

        compose.onNode(hasText(string(R.string.settings_export_title)) and hasClickAction())
            .performScrollTo()
            .performClick()

        assertEquals(1, requested)
        assertEquals(0, confirmed)
        assertEquals(0, dismissed)
    }

    @Test
    fun noDialogIsShownUntilTheStateAsksForOne() {
        show(showConfirmation = false)

        compose.onNodeWithText(string(R.string.settings_export_confirm_message)).assertDoesNotExist()
    }

    @Test
    fun theConfirmationStateShowsTheDisclosureDialog() {
        show(showConfirmation = true)

        compose.onNodeWithText(string(R.string.settings_export_confirm_message)).assertExists()
    }

    @Test
    fun confirmingTheDialogCallsConfirmOnly() {
        show(showConfirmation = true)

        compose.onNode(
            hasText(string(R.string.settings_export_title)) and hasClickAction() and
                hasAnyAncestor(isDialog()),
        ).performClick()

        assertEquals(1, confirmed)
        assertEquals(0, dismissed)
        assertEquals(0, requested)
    }

    @Test
    fun cancellingTheDialogCallsDismissOnly() {
        show(showConfirmation = true)

        compose.onNodeWithText(string(R.string.email_detail_delete_cancel)).performClick()

        assertEquals(1, dismissed)
        assertEquals(0, confirmed)
        assertEquals(0, requested)
    }
}
