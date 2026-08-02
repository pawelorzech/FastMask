package com.fastmask.ui.settings

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

class ExportConfirmationWiringTest {

    @Test
    fun `settings row and dialog are wired to separate request confirm and dismiss actions`() {
        val source = File("src/main/java/com/fastmask/ui/settings/SettingsScreen.kt").readText()

        assertTrue(source.contains("onClick = viewModel::onExportClick"))
        assertTrue(source.contains("if (uiState.showExportConfirmation)"))
        assertTrue(source.contains("onConfirm = viewModel::onExportConfirmed"))
        assertTrue(source.contains("onDismiss = viewModel::onExportConfirmationDismissed"))
    }
}
