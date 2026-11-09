package com.fastmask.ui.detail

import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.tooling.preview.Preview
import com.fastmask.R
import com.fastmask.domain.model.EmailState
import com.fastmask.domain.model.MaskedEmail
import com.fastmask.ui.common.UiError
import com.fastmask.ui.common.UiErrorAction
import com.fastmask.ui.theme.FastMaskTheme
import java.time.Instant

private val previewMask = MaskedEmail(
    id = "m1",
    email = "sharp.flame138@fastmail.com",
    state = EmailState.ENABLED,
    forDomain = "figma.com",
    description = "Figma account",
    createdBy = "FastMask",
    url = "https://www.figma.com/login",
    emailPrefix = "sharp",
    createdAt = Instant.parse("2026-06-01T10:00:00Z"),
    lastMessageAt = Instant.parse("2026-10-20T08:30:00Z"),
)

private fun previewState(email: MaskedEmail = previewMask) = MaskedEmailDetailUiState(
    email = email,
    editedDescription = email.description.orEmpty(),
    editedForDomain = email.forDomain.orEmpty(),
    editedUrl = email.url.orEmpty(),
)

@Composable
private fun PreviewHost(uiState: MaskedEmailDetailUiState) {
    FastMaskTheme {
        MaskedEmailDetailContent(
            uiState = uiState,
            containerId = null,
            snackbarHostState = remember { SnackbarHostState() },
            banner = {},
            onBack = {},
            onRetry = {},
            onReauthenticate = {},
            onDescriptionChange = {},
            onForDomainChange = {},
            onUrlChange = {},
            onEnable = {},
            onDisable = {},
            onSaveChanges = {},
            onStartEditing = {},
            onDiscardEdits = {},
            onCopyEmail = {},
            onArchiveClick = {},
        )
    }
}

@Preview(name = "Editing", showBackground = true, heightDp = 1000)
@Composable
private fun DetailEditingPreview() = PreviewHost(
    previewState().copy(isEditing = true, editedDescription = "Figma work account"),
)

@Preview(name = "Active", showBackground = true, heightDp = 900)
@Composable
private fun DetailActivePreview() = PreviewHost(previewState())

@Preview(
    name = "Active dark",
    showBackground = true,
    heightDp = 900,
    uiMode = android.content.res.Configuration.UI_MODE_NIGHT_YES,
)
@Composable
private fun DetailActiveDarkPreview() = PreviewHost(previewState())

@Preview(name = "Archived", showBackground = true, heightDp = 900)
@Composable
private fun DetailArchivedPreview() =
    PreviewHost(previewState(previewMask.copy(state = EmailState.DELETED)))

@Preview(name = "Toggling", showBackground = true, heightDp = 900)
@Composable
private fun DetailTogglingPreview() =
    PreviewHost(previewState().copy(updateOperation = MaskedEmailDetailUpdate.TOGGLE_STATE))

@Preview(name = "Loading", showBackground = true)
@Composable
private fun DetailLoadingPreview() = PreviewHost(MaskedEmailDetailUiState(isLoading = true))

@Preview(name = "Error", showBackground = true)
@Composable
private fun DetailErrorPreview() =
    PreviewHost(MaskedEmailDetailUiState(error = UiError(R.string.email_detail_error_load, UiErrorAction.RETRY)))

@Preview(name = "Long address", showBackground = true, heightDp = 900)
@Composable
private fun DetailLongAddressPreview() = PreviewHost(
    previewState(previewMask.copy(email = "a-very-long-prefix-name-for-testing-wrapping.bright4821@fastmail.com")),
)
