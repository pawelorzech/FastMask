package com.fastmask.ui.hygiene

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.fastmask.R
import com.fastmask.domain.hygiene.HygieneIssue
import com.fastmask.domain.model.EmailState
import com.fastmask.ui.components.ConfirmDialog
import com.fastmask.ui.components.DesignCard
import com.fastmask.ui.components.ErrorMessage
import com.fastmask.ui.components.HairlineDivider
import com.fastmask.ui.components.LoadingIndicator
import com.fastmask.ui.components.MonoEyebrow
import com.fastmask.ui.components.MonoLabel
import com.fastmask.ui.components.PillButton
import com.fastmask.ui.components.PillButtonVariant
import com.fastmask.ui.components.PillIconButton
import com.fastmask.ui.components.StatePill
import com.fastmask.ui.theme.FastMaskExtras
import com.fastmask.ui.util.RelativeTime

@Composable
fun MaskHygieneScreen(
    onNavigateBack: () -> Unit,
    onNavigateToPro: (String) -> Unit,
    viewModel: MaskHygieneViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val extras = FastMaskExtras.current
    val context = LocalContext.current
    // Saveable: the confirmation must survive a rotation, exactly like the
    // selection it is about (which lives in the ViewModel and already does).
    var pendingActionName by rememberSaveable { mutableStateOf<String?>(null) }
    val pendingAction: BulkAction? = pendingActionName?.let { name ->
        runCatching { BulkAction.valueOf(name) }.getOrNull()
    }
    val report = uiState.report
    val attentionCount = (report.reviewedCount - report.healthyCount).coerceAtLeast(0)
    val reviewedSummary = pluralStringResource(
        R.plurals.hygiene_reviewed,
        report.reviewedCount,
        report.reviewedCount,
    )
    val attentionSummary = pluralStringResource(
        R.plurals.hygiene_needs_attention,
        attentionCount,
        attentionCount,
    )
    // Hoisted so the rows below receive the same instance on every recomposition
    // and stay skippable when only the selection changed.
    val onToggleMask: (String) -> Unit = remember(viewModel) { viewModel::onMaskToggled }

    /**
     * Back is swallowed outright while a run is in flight. The run is N separate
     * account mutations; leaving halfway would archive an unknown number of
     * masks and take the count, the snackbar and the undo with it.
     */
    BackHandler(enabled = uiState.actionInFlight || uiState.selectedIds.isNotEmpty()) {
        if (!uiState.actionInFlight) {
            viewModel.onClearSelection()
        }
    }

    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                is MaskHygieneEvent.OpenPro -> onNavigateToPro(event.source)
                is MaskHygieneEvent.BulkActionFinished -> {
                    val result = event.result
                    val message = when {
                        result.isCompleteSuccess -> {
                            when (result.action) {
                                BulkAction.DISABLE -> context.resources.getQuantityString(
                                    R.plurals.hygiene_bulk_done_disabled,
                                    result.succeeded.size,
                                    result.succeeded.size,
                                )

                                BulkAction.ARCHIVE -> context.resources.getQuantityString(
                                    R.plurals.hygiene_bulk_done_archived,
                                    result.succeeded.size,
                                    result.succeeded.size,
                                )
                            }
                        }

                        result.isPartial -> context.getString(
                            R.string.hygiene_bulk_partial,
                            result.succeeded.size,
                            result.requested,
                            result.failedIds.size,
                        )

                        else -> context.resources.getQuantityString(
                            R.plurals.hygiene_bulk_failed,
                            result.failedIds.size,
                            result.failedIds.size,
                        )
                    }
                    val actionLabel = if (result.succeeded.isNotEmpty()) {
                        context.getString(R.string.hygiene_undo)
                    } else {
                        null
                    }
                    val snackbarResult = snackbarHostState.showSnackbar(
                        message = message,
                        actionLabel = actionLabel,
                    )
                    if (actionLabel != null && snackbarResult == SnackbarResult.ActionPerformed) {
                        viewModel.undoBulkAction(result)
                    }
                }
            }
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
        bottomBar = {
            if (uiState.selectedIds.isNotEmpty()) {
                Surface {
                    Column {
                        HairlineDivider()
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 20.dp, vertical = 16.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            MonoEyebrow(
                                text = pluralStringResource(
                                    R.plurals.hygiene_selected_count,
                                    uiState.selectedIds.size,
                                    uiState.selectedIds.size,
                                ),
                            )
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                PillButton(
                                    text = stringResource(R.string.hygiene_action_disable),
                                    onClick = { pendingActionName = BulkAction.DISABLE.name },
                                    modifier = Modifier.weight(1f),
                                    variant = PillButtonVariant.Secondary,
                                    enabled = !uiState.actionInFlight,
                                    loading = uiState.actionInFlight,
                                )
                                PillButton(
                                    text = stringResource(R.string.hygiene_action_archive),
                                    onClick = { pendingActionName = BulkAction.ARCHIVE.name },
                                    modifier = Modifier.weight(1f),
                                    variant = PillButtonVariant.Danger,
                                    enabled = !uiState.actionInFlight,
                                    loading = uiState.actionInFlight,
                                )
                                PillButton(
                                    text = stringResource(R.string.hygiene_clear_selection),
                                    onClick = viewModel::onClearSelection,
                                    variant = PillButtonVariant.Ghost,
                                    enabled = !uiState.actionInFlight,
                                )
                            }
                        }
                    }
                }
            }
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 20.dp, vertical = 16.dp),
        ) {
            MonoEyebrow(
                text = stringResource(
                    R.string.hygiene_summary,
                    reviewedSummary,
                    attentionSummary,
                ),
                color = extras.inkMuted,
            )
            Spacer(modifier = Modifier.size(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                PillIconButton(
                    onClick = {
                        // Same rule as the back gesture: no exit mid-run.
                        if (uiState.actionInFlight) return@PillIconButton
                        if (uiState.selectedIds.isNotEmpty()) {
                            viewModel.onClearSelection()
                        } else {
                            onNavigateBack()
                        }
                    },
                    contentDescription = stringResource(R.string.hygiene_back),
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                }
                androidx.compose.material3.Text(
                    text = stringResource(R.string.hygiene_title),
                    style = MaterialTheme.typography.displayMedium,
                )
            }
            if (uiState.errorRes != null && report.reviewedCount > 0) {
                Spacer(modifier = Modifier.size(12.dp))
                HygieneErrorBanner(
                    message = stringResource(uiState.errorRes!!),
                    onRetry = viewModel::refresh,
                    enabled = !uiState.actionInFlight,
                )
            }
            Spacer(modifier = Modifier.size(20.dp))

            when {
                uiState.isLoading && report.isClean -> {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        contentAlignment = Alignment.Center,
                    ) {
                        LoadingIndicator()
                    }
                }

                // Only a cold load with nothing to show gets the whole screen.
                // A refresh that failed on top of a report the user is already
                // reading — the common case after a partial bulk run, where the
                // surviving selection IS the retry list — gets a banner instead.
                uiState.errorRes != null && report.reviewedCount == 0 -> {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        contentAlignment = Alignment.Center,
                    ) {
                        ErrorMessage(
                            message = stringResource(uiState.errorRes!!),
                            onRetry = viewModel::refresh,
                        )
                    }
                }

                // Nothing was ever created, as opposed to everything having
                // been archived: `totalCount` is the count before archived
                // masks are filtered out, and telling a tidy user with forty
                // archived masks that they have none is a lie about their own
                // account.
                report.totalCount == 0 -> {
                    HygieneStateCard(
                        modifier = Modifier.weight(1f),
                        title = stringResource(R.string.hygiene_empty_title),
                        body = stringResource(R.string.hygiene_empty_body),
                    )
                }

                report.reviewedCount == 0 -> {
                    HygieneStateCard(
                        modifier = Modifier.weight(1f),
                        title = stringResource(R.string.hygiene_clean_title),
                        body = stringResource(R.string.hygiene_all_archived_body),
                    )
                }

                report.isClean -> {
                    HygieneStateCard(
                        modifier = Modifier.weight(1f),
                        title = stringResource(R.string.hygiene_clean_title),
                        body = stringResource(R.string.hygiene_clean_body),
                    )
                }

                else -> {
                    // One flat lazy feed rather than a lazy list of eagerly
                    // rendered sections: a collection with hundreds of masks
                    // used to compose, measure and lay out every row of every
                    // group on the first frame — twice over for a mask that
                    // falls into two categories.
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                    ) {
                        report.groups.forEach { group ->
                            item(
                                key = "header:${group.issue.name}",
                                contentType = HYGIENE_HEADER,
                            ) {
                                HygieneGroupHeader(
                                    issue = group.issue,
                                    count = group.masks.size,
                                    actionInFlight = uiState.actionInFlight,
                                    onSelectAll = { viewModel.onSelectAll(group.issue) },
                                )
                            }
                            items(
                                count = group.masks.size,
                                // The same mask may legitimately appear under
                                // two issues, so the issue is part of the key.
                                key = { index -> "${group.issue.name}:${group.masks[index].id}" },
                                contentType = { HYGIENE_ROW },
                            ) { index ->
                                val mask = group.masks[index]
                                val timestampLabel = when (group.issue) {
                                    HygieneIssue.NEVER_USED -> stringResource(
                                        R.string.hygiene_created,
                                        RelativeTime.format(context, mask.createdAt),
                                    )

                                    else -> stringResource(
                                        R.string.hygiene_last_message,
                                        RelativeTime.format(context, mask.lastMessageAt),
                                    )
                                }
                                HygieneMaskRow(
                                    id = mask.id,
                                    displayName = mask.displayName,
                                    email = mask.email,
                                    state = mask.state,
                                    // Resolved to a Boolean here so ticking one
                                    // checkbox cannot invalidate every other row.
                                    selected = mask.id in uiState.selectedIds,
                                    timestampLabel = timestampLabel,
                                    onToggle = onToggleMask,
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    pendingAction?.let { action ->
        ConfirmDialog(
            title = stringResource(
                if (action == BulkAction.DISABLE) {
                    R.string.hygiene_confirm_disable_title
                } else {
                    R.string.hygiene_confirm_archive_title
                },
            ),
            message = stringResource(
                if (action == BulkAction.DISABLE) {
                    R.string.hygiene_confirm_disable_body
                } else {
                    R.string.hygiene_confirm_archive_body
                },
            ),
            confirmText = stringResource(
                if (action == BulkAction.DISABLE) {
                    R.string.hygiene_action_disable
                } else {
                    R.string.hygiene_action_archive
                },
            ),
            dismissText = stringResource(R.string.hygiene_confirm_cancel),
            onConfirm = {
                pendingActionName = null
                viewModel.onBulkAction(action)
            },
            onDismiss = { pendingActionName = null },
            confirmVariant = if (action == BulkAction.DISABLE) {
                PillButtonVariant.Secondary
            } else {
                PillButtonVariant.Danger
            },
        )
    }
}

/** Item types, so the lazy list reuses a header slot only for another header. */
private const val HYGIENE_HEADER = "hygiene_header"
private const val HYGIENE_ROW = "hygiene_row"

/** Non-destructive: says the refresh failed without taking the report away. */
@Composable
private fun HygieneErrorBanner(
    message: String,
    onRetry: () -> Unit,
    enabled: Boolean,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        androidx.compose.material3.Text(
            text = message,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.error,
        )
        PillButton(
            text = stringResource(R.string.error_retry),
            onClick = onRetry,
            variant = PillButtonVariant.Ghost,
            enabled = enabled,
        )
    }
}

@Composable
private fun HygieneStateCard(
    title: String,
    body: String,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier.fillMaxWidth(),
        contentAlignment = Alignment.Center,
    ) {
        DesignCard(
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                androidx.compose.material3.Text(
                    text = title,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold,
                )
                androidx.compose.material3.Text(
                    text = body,
                    style = MaterialTheme.typography.bodyMedium,
                    color = FastMaskExtras.current.inkSoft,
                )
            }
        }
    }
}

@Composable
private fun HygieneGroupHeader(
    issue: HygieneIssue,
    count: Int,
    actionInFlight: Boolean,
    onSelectAll: () -> Unit,
) {
    val extras = FastMaskExtras.current
    val headerColor = if (issue == HygieneIssue.NEW_ACTIVITY) extras.accent else null

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 16.dp, bottom = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Top,
    ) {
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            androidx.compose.material3.Text(
                text = issueTitle(issue),
                style = MaterialTheme.typography.titleLarge,
                color = headerColor ?: MaterialTheme.colorScheme.onSurface,
            )
            MonoLabel(
                text = pluralStringResource(R.plurals.hygiene_group_count, count, count),
                color = extras.inkMuted,
            )
            androidx.compose.material3.Text(
                text = issueBody(issue),
                style = MaterialTheme.typography.bodyMedium,
                color = extras.inkSoft,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(modifier = Modifier.size(12.dp))
        PillButton(
            text = stringResource(R.string.hygiene_select_all),
            onClick = onSelectAll,
            variant = PillButtonVariant.Ghost,
            enabled = !actionInFlight,
        )
    }
}

/**
 * Takes only stable values, never the [com.fastmask.domain.model.MaskedEmail]
 * itself: the model carries `Instant`s, which Compose cannot prove immutable,
 * so passing it would make every row unskippable and undo the point of the
 * per-row `selected` flag.
 */
@Composable
private fun HygieneMaskRow(
    id: String,
    displayName: String,
    email: String,
    state: EmailState,
    selected: Boolean,
    timestampLabel: String,
    onToggle: (String) -> Unit,
) {
    val extras = FastMaskExtras.current

    DesignCard(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 10.dp),
        onClick = { onToggle(id) },
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.Top,
        ) {
            Checkbox(
                checked = selected,
                onCheckedChange = { onToggle(id) },
            )
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                androidx.compose.material3.Text(
                    text = displayName,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                MonoLabel(
                    text = email,
                    color = extras.inkMuted,
                )
                StatePill(
                    state = state,
                    label = stringResource(stateLabel(state)),
                )
                androidx.compose.material3.Text(
                    text = timestampLabel,
                    style = MaterialTheme.typography.bodySmall,
                    color = extras.inkSoft,
                )
            }
        }
    }
}

@Composable
private fun issueTitle(issue: HygieneIssue): String = when (issue) {
    HygieneIssue.NEW_ACTIVITY -> stringResource(R.string.hygiene_issue_new_title)
    HygieneIssue.NEVER_USED -> stringResource(R.string.hygiene_issue_never_title)
    HygieneIssue.DORMANT -> stringResource(R.string.hygiene_issue_dormant_title)
    HygieneIssue.UNDESCRIBED -> stringResource(R.string.hygiene_issue_undescribed_title)
}

@Composable
private fun issueBody(issue: HygieneIssue): String = when (issue) {
    HygieneIssue.NEW_ACTIVITY -> stringResource(R.string.hygiene_issue_new_body)
    HygieneIssue.NEVER_USED -> stringResource(R.string.hygiene_issue_never_body)
    HygieneIssue.DORMANT -> stringResource(R.string.hygiene_issue_dormant_body)
    HygieneIssue.UNDESCRIBED -> stringResource(R.string.hygiene_issue_undescribed_body)
}

/**
 * Reuses the state labels the list and detail screens already show, which are
 * translated into all 20 locales. Deriving the label from `EmailState.name`
 * instead would have printed raw English enum names on this one screen.
 */
private fun stateLabel(state: EmailState): Int = when (state) {
    EmailState.ENABLED -> R.string.state_enabled
    EmailState.DISABLED -> R.string.state_disabled
    EmailState.DELETED -> R.string.state_deleted
    EmailState.PENDING -> R.string.state_pending
}
