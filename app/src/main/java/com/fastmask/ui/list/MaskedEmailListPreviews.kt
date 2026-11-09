package com.fastmask.ui.list

import android.content.res.Configuration
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.semantics.text
import androidx.compose.ui.tooling.preview.Preview
import com.fastmask.R
import com.fastmask.domain.model.EmailState
import com.fastmask.domain.model.MaskedEmail
import com.fastmask.ui.common.UiError
import com.fastmask.ui.common.UiErrorAction
import com.fastmask.ui.components.DemoBannerContent
import com.fastmask.ui.components.InlineErrorBanner
import com.fastmask.ui.theme.FastMaskTheme
import java.time.Instant


private fun previewMask(
    id: String,
    email: String,
    state: EmailState = EmailState.ENABLED,
    note: String? = null,
    domain: String? = null,
    ageSeconds: Long,
): MaskedEmail {
    val moment = Instant.now().minusSeconds(ageSeconds)
    return MaskedEmail(
        id = id,
        email = email,
        state = state,
        forDomain = domain,
        description = note,
        createdBy = null,
        url = null,
        emailPrefix = null,
        createdAt = moment,
        lastMessageAt = moment,
    )
}

private val previewMasks = listOf(
    previewMask("1", "amber.otter4821@fastmail.com", note = "Newsletter", domain = "news.ycombinator.com", ageSeconds = 600),
    previewMask("2", "shop.brisk@fastmail.com", state = EmailState.PENDING, domain = "amazon.pl", ageSeconds = 3 * 3_600),
    previewMask("3", "gh.calm1337@fastmail.com", note = "Work repos", domain = "github.com", ageSeconds = 2 * 86_400L),
    previewMask("4", "travel.lucid.longaddress.that.truncates@fastmail.com", state = EmailState.DISABLED, domain = "booking.com", ageSeconds = 12 * 86_400L),
    previewMask("5", "old.grove@fastmail.com", state = EmailState.DELETED, note = "Retired", ageSeconds = 90 * 86_400L),
)

@Composable
private fun PreviewList(
    state: MaskedEmailListUiState,
    showDemo: Boolean = false,
    highlight: String? = null,
    menuExpanded: Boolean = false,
) {
    FastMaskTheme {
        MaskedEmailListContent(
            uiState = state,
            snackbarHostState = remember { SnackbarHostState() },
            listState = rememberLazyListState(),
            highlightEmail = highlight,
            onSearchQueryChange = {},
            onFilterSelect = {},
            onRowOpen = {},
            onRowCopy = {},
            onRetry = {},
            onReauthenticate = {},
            onRefresh = {},
            onNavigateToCreate = {},
            onRandomMask = {},
            onNavigateToSettings = {},
            onTutorialBounds = { _, _ -> },
            demoBanner = { if (showDemo) DemoBannerContent(onSignInClick = {}) },
            initialMenuExpanded = menuExpanded,
        )
    }
}

private fun populated() = MaskedEmailListUiState(emails = previewMasks)

@Preview(name = "List", showBackground = true, heightDp = 800)
@Composable
private fun ListPopulatedPreview() = PreviewList(populated(), showDemo = true, highlight = "1")

@Preview(
    name = "List dark",
    showBackground = true,
    heightDp = 800,
    uiMode = Configuration.UI_MODE_NIGHT_YES,
)
@Composable
private fun ListPopulatedDarkPreview() = PreviewList(populated())

@Preview(name = "List FAB menu", showBackground = true, heightDp = 800)
@Composable
private fun ListFabMenuPreview() = PreviewList(populated(), menuExpanded = true)

@Preview(name = "List loading", showBackground = true, heightDp = 800)
@Composable
private fun ListLoadingPreview() = PreviewList(MaskedEmailListUiState(isLoading = true))

@Preview(name = "List empty", showBackground = true, heightDp = 800)
@Composable
private fun ListEmptyPreview() = PreviewList(MaskedEmailListUiState())

@Preview(
    name = "List empty dark",
    showBackground = true,
    heightDp = 800,
    uiMode = Configuration.UI_MODE_NIGHT_YES,
)
@Composable
private fun ListEmptyDarkPreview() = PreviewList(MaskedEmailListUiState())

@Preview(name = "List no matches", showBackground = true, heightDp = 800)
@Composable
private fun ListNoMatchesPreview() = PreviewList(
    MaskedEmailListUiState(emails = previewMasks, searchQuery = "zzz"),
)

@Preview(name = "List error", showBackground = true, heightDp = 800)
@Composable
private fun ListErrorPreview() = PreviewList(
    MaskedEmailListUiState(error = UiError(R.string.error_load_emails, UiErrorAction.RETRY)),
)

@Preview(name = "List offline", showBackground = true, heightDp = 800)
@Composable
private fun ListOfflinePreview() = PreviewList(
    populated().copy(
        cachedAt = Instant.now().minusSeconds(7_200),
        selectedFilter = EmailFilter.ENABLED,
    ),
)

@Preview(name = "Inline error", showBackground = true)
@Composable
private fun InlineErrorPreview() = FastMaskTheme {
    InlineErrorBanner(
        error = UiError(R.string.error_network, UiErrorAction.RETRY),
        onRetry = {},
        onReauthenticate = {},
    )
}
