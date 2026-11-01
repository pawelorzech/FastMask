package com.fastmask.ui.settings

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.fastmask.ui.theme.FastMaskTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LicensesDialogTest {

    @get:Rule
    val compose = createComposeRule()

    @Test
    fun namesBothComponentsAndShowsTheirLicenseText() {
        compose.setContent { FastMaskTheme { LicensesDialog(onDismiss = {}) } }

        compose.onNodeWithText("Open-source licenses").assertIsDisplayed()
        compose.onNodeWithText("Google Sans Flex (font)").assertIsDisplayed()
        compose.onNodeWithText("SIL OPEN FONT LICENSE Version 1.1", substring = true)
            .performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Material Symbols", substring = true).performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Apache License 2.0").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun closeDismisses() {
        var dismissed = 0
        compose.setContent { FastMaskTheme { LicensesDialog(onDismiss = { dismissed++ }) } }

        compose.onNodeWithText("Close").performClick()

        assertEquals(1, dismissed)
    }
}
