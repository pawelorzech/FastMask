package com.fastmask.ui.accessibility

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

class AccessibilitySemanticsWiringTest {

    @Test
    fun allMissingScreenTitlesUseHeadingSemantics() {
        val titleAnchors = mapOf(
            "auth/LoginScreen.kt" to "text = annotated",
            "welcome/WelcomeScreen.kt" to "text = stringResource(R.string.app_name)",
            "detail/MaskedEmailDetailScreen.kt" to "text = email.displayName",
            "lock/LockScreen.kt" to "text = stringResource(R.string.app_name)",
        )

        titleAnchors.forEach { (relativePath, titleAnchor) ->
            assertNear(relativePath, titleAnchor, "modifier = Modifier.screenHeading()")
        }
    }

    @Test
    fun allRadioButtonContainersExposeTheirGroupRelationship() {
        assertInFunction(
            "create/CreateMaskedEmailScreen.kt",
            "private fun StateSegmented(",
            ".radioButtonGroup()",
        )
        assertInFunction(
            "settings/SettingsScreen.kt",
            "private fun AccentPickerDialog(",
            "Column(modifier = Modifier.radioButtonGroup())",
        )
        assertInFunction(
            "settings/SettingsScreen.kt",
            "private fun LanguagePickerDialog(",
            "LazyColumn(modifier = Modifier.radioButtonGroup())",
        )
    }

    @Test
    fun everyDynamicStateHasAPoliteAnnouncementPath() {
        assertNear("auth/LoginScreen.kt", "text = stringResource(warningRes)", ".politeLiveRegion()")
        assertNear(
            "auth/LoginScreen.kt",
            "text = stringResource(R.string.login_open_browser_failed)",
            ".politeLiveRegion()",
        )
        assertNear(
            "create/CreateMaskedEmailScreen.kt",
            "text = stringResource(uiState.errorRes!!)",
            ".politeLiveRegion()",
        )
        assertNear(
            "detail/MaskedEmailDetailScreen.kt",
            "text = stringResource(uiState.errorRes)",
            ".politeLiveRegion()",
        )
        assertInFunction(
            "settings/SettingsScreen.kt",
            "private fun SettingsRow(",
            ".politeLiveRegion()",
        )
        assertInFunction(
            "list/MaskedEmailListScreen.kt",
            "private fun LoadingShimmer(",
            ".politeLiveRegion()",
        )
        assertInFunction(
            "list/MaskedEmailListScreen.kt",
            "private fun ErrorBlock(",
            ".politeLiveRegion()",
        )
    }

    @Test
    fun resultCountUsesFilteredAndTotalCollections() {
        assertNear(
            "list/MaskedEmailListScreen.kt",
            "R.string.email_list_result_count",
            "uiState.filteredEmails.size",
        )
        assertNear(
            "list/MaskedEmailListScreen.kt",
            "R.string.email_list_result_count",
            "uiState.emails.size",
        )
        assertNear(
            "list/MaskedEmailListScreen.kt",
            "R.string.email_list_result_count",
            ".politeLiveRegion()",
        )
    }

    private fun source(relativePath: String): String =
        File("src/main/java/com/fastmask/ui/$relativePath").readText()

    private fun assertNear(
        relativePath: String,
        anchor: String,
        required: String,
        radius: Int = 500,
    ) {
        val source = source(relativePath)
        val anchorIndex = source.indexOf(anchor)
        assertTrue("$relativePath must contain anchor '$anchor'", anchorIndex >= 0)
        val neighborhood = source.substring(
            startIndex = (anchorIndex - radius).coerceAtLeast(0),
            endIndex = (anchorIndex + anchor.length + radius).coerceAtMost(source.length),
        )
        assertTrue(
            "$relativePath must contain '$required' near '$anchor'",
            neighborhood.contains(required),
        )
    }

    private fun assertInFunction(relativePath: String, signature: String, required: String) {
        val source = source(relativePath)
        val start = source.indexOf(signature)
        assertTrue("$relativePath must contain function '$signature'", start >= 0)
        val nextComposable = source.indexOf("\n@Composable", start + signature.length)
        val body = source.substring(start, if (nextComposable >= 0) nextComposable else source.length)
        assertTrue(
            "$relativePath function '$signature' must contain '$required'",
            body.contains(required),
        )
    }
}
