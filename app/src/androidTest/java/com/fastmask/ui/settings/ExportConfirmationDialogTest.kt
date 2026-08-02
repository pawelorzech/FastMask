package com.fastmask.ui.settings

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.fastmask.R
import com.fastmask.ui.theme.FastMaskTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ExportConfirmationDialogTest {

    @get:Rule
    val compose = createComposeRule()

    private val context = ApplicationProvider.getApplicationContext<android.content.Context>()

    @Test
    fun disclosureIsShownAndCancelOnlyDismisses() {
        var confirmed = 0
        var dismissed = 0
        compose.setContent {
            FastMaskTheme {
                ExportConfirmationDialog(
                    onConfirm = { confirmed += 1 },
                    onDismiss = { dismissed += 1 },
                )
            }
        }

        compose.onNodeWithText(context.getString(R.string.settings_export_confirm_message))
            .assertExists()
        compose.onNodeWithText(context.getString(R.string.email_detail_delete_cancel))
            .performClick()

        assertEquals(0, confirmed)
        assertEquals(1, dismissed)
    }

    @Test
    fun exportButtonOnlyConfirms() {
        var confirmed = 0
        var dismissed = 0
        compose.setContent {
            FastMaskTheme {
                ExportConfirmationDialog(
                    onConfirm = { confirmed += 1 },
                    onDismiss = { dismissed += 1 },
                )
            }
        }

        compose.onNode(
            hasText(context.getString(R.string.settings_export_title)) and hasClickAction(),
        )
            .performClick()

        assertEquals(1, confirmed)
        assertEquals(0, dismissed)
    }
}
