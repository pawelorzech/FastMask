@file:OptIn(ExperimentalMaterial3AdaptiveApi::class, ExperimentalMaterial3ExpressiveApi::class)

package com.fastmask.ui.adaptive

import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AlternateEmail
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalMinimumInteractiveComponentSize
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDragHandle
import androidx.compose.material3.adaptive.ExperimentalMaterial3AdaptiveApi
import androidx.compose.material3.adaptive.layout.AnimatedPane
import androidx.compose.material3.adaptive.layout.ListDetailPaneScaffoldRole
import androidx.compose.material3.adaptive.layout.PaneAdaptedValue
import androidx.compose.material3.adaptive.layout.PaneExpansionAnchor
import androidx.compose.material3.adaptive.layout.PaneExpansionState
import androidx.compose.material3.adaptive.layout.ThreePaneScaffoldRole
import androidx.compose.material3.adaptive.layout.ThreePaneScaffoldScope
import androidx.compose.material3.adaptive.layout.ThreePaneScaffoldValue
import androidx.compose.material3.adaptive.layout.rememberPaneExpansionState
import androidx.compose.material3.adaptive.navigation.NavigableListDetailPaneScaffold
import androidx.compose.material3.adaptive.navigation.ThreePaneScaffoldNavigator
import androidx.compose.material3.adaptive.navigation.rememberListDetailPaneScaffoldNavigator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.fastmask.R
import com.fastmask.ui.components.expressive.MorphingShape
import com.fastmask.ui.create.CreateMaskedEmailScreen
import com.fastmask.ui.create.CreateMaskedEmailViewModel
import com.fastmask.ui.detail.MaskedEmailDetailScreen
import com.fastmask.ui.detail.MaskedEmailDetailViewModel
import com.fastmask.ui.list.ListResult
import com.fastmask.ui.list.MaskedEmailListScreen
import com.fastmask.ui.list.MaskedEmailListViewModel
import kotlinx.coroutines.launch

@Composable
fun MasksWorkspace(
    onNavigateToSettings: () -> Unit,
    onSignInFromBanner: () -> Unit,
    onReauthenticate: () -> Unit,
    externalPendingResult: ListResult? = null,
    onExternalResultConsumed: (ListResult) -> Unit = {},
    workspaceViewModel: WorkspaceViewModel = hiltViewModel(),
    listViewModel: MaskedEmailListViewModel = hiltViewModel(),
    modifier: Modifier = Modifier,
) {
    val adaptiveInfo = currentFastMaskAdaptiveInfo()
    val detailPane by workspaceViewModel.detailPane.collectAsState()
    val workspacePendingResult by workspaceViewModel.pendingListResult.collectAsState()
    val scope = rememberCoroutineScope()

    LaunchedEffect(externalPendingResult) {
        if (externalPendingResult != null) {
            workspaceViewModel.postListResult(externalPendingResult)
            onExternalResultConsumed(externalPendingResult)
        }
    }

    val directive = FastMaskAdaptiveRules.paneDirective(adaptiveInfo, allowTwoPanesOnMedium = false)
    val navigator = rememberListDetailPaneScaffoldNavigator<String>(scaffoldDirective = directive)
    val expansionState = rememberPaneExpansionState(
        keyProvider = navigator.scaffoldValue,
        anchors = listOf(
            PaneExpansionAnchor.Proportion(0.36f),
            PaneExpansionAnchor.Proportion(0.45f),
            PaneExpansionAnchor.Proportion(0.56f),
        ),
    )

    val isSinglePane = directive.maxHorizontalPartitions == 1
    val detailStandsAlone = detailIsOnlyVisiblePane(navigator.scaffoldValue, ListDetailPaneScaffoldRole.Detail)
    val selectedEmailId = (detailPane as? WorkspaceDetailPane.Detail)?.emailId

    LaunchedEffect(detailPane) {
        when (val pane = detailPane) {
            is WorkspaceDetailPane.Detail -> {
                val key = "detail:${pane.emailId}"
                if (navigator.currentDestination?.pane != ListDetailPaneScaffoldRole.Detail ||
                    navigator.currentDestination?.contentKey != key
                ) {
                    navigator.navigateTo(ListDetailPaneScaffoldRole.Detail, key)
                }
            }
            is WorkspaceDetailPane.Create -> {
                val key = "create:${pane.forDomain}"
                if (navigator.currentDestination?.pane != ListDetailPaneScaffoldRole.Detail ||
                    navigator.currentDestination?.contentKey != key
                ) {
                    navigator.navigateTo(ListDetailPaneScaffoldRole.Detail, key)
                }
            }
            WorkspaceDetailPane.None -> {
                if (navigator.currentDestination?.pane == ListDetailPaneScaffoldRole.Detail) {
                    navigator.navigateTo(ListDetailPaneScaffoldRole.List)
                }
            }
        }
    }

    // System/gesture back in single-pane mode leaves the detail pane without our toolbar callback;
    // clear the selection so the detail pane does not immediately reopen on recomposition.
    ForgetSelectionWhenPaneLeft(navigator, ListDetailPaneScaffoldRole.Detail, workspaceViewModel::clearDetail)

    NavigableListDetailPaneScaffold(
        navigator = navigator,
        paneExpansionState = expansionState,
        paneExpansionDragHandle = paneDragHandleIfFlat(adaptiveInfo),
        modifier = modifier.fillMaxSize().testTag("masks_workspace"),
        listPane = {
            AnimatedPane(Modifier.preferredWidth(380.dp).focusGroup().testTag("pane_masks_list")) {
                MaskedEmailListScreen(
                    onNavigateToCreate = {
                        workspaceViewModel.openCreate()
                    },
                    onNavigateToDetail = { emailId ->
                        workspaceViewModel.selectEmail(emailId)
                    },
                    onNavigateToSettings = onNavigateToSettings,
                    onSignInFromBanner = onSignInFromBanner,
                    onReauthenticate = onReauthenticate,
                    pendingResult = workspacePendingResult,
                    onResultConsumed = workspaceViewModel::consumeListResult,
                    selectedEmailId = if (!isSinglePane) selectedEmailId else null,
                    inPane = true,
                    onRandomCreated = { id, _ ->
                        if (!isSinglePane) {
                            workspaceViewModel.selectEmail(id)
                        }
                    },
                    viewModel = listViewModel,
                )
            }
        },
        detailPane = {
            AnimatedPane(Modifier.focusGroup().testTag("pane_masks_detail")) {
                when (val pane = detailPane) {
                    WorkspaceDetailPane.None -> MaskDetailEmptyPlaceholder()
                    is WorkspaceDetailPane.Detail -> {
                        val detailVm: MaskedEmailDetailViewModel = hiltViewModel(key = "ws_detail")
                        // Hand the selection to the detail ViewModel once per change, in composition,
                        // so frame 0 reads from the RAM cache synchronously without bouncing.
                        remember(detailVm) { DetailSelection() }.follow(detailVm, pane.emailId)
                        MaskedEmailDetailScreen(
                            onNavigateBack = {
                                workspaceViewModel.clearDetail()
                                scope.launch {
                                    if (navigator.canNavigateBack()) navigator.navigateBack()
                                }
                            },
                            onArchived = { id, previousState ->
                                workspaceViewModel.onMaskArchived(id, previousState)
                                scope.launch {
                                    if (isSinglePane && navigator.canNavigateBack()) {
                                        navigator.navigateBack()
                                    }
                                }
                            },
                            onSignInFromBanner = onSignInFromBanner,
                            onReauthenticate = onReauthenticate,
                            showBackButton = detailStandsAlone,
                            showDemoBanner = detailStandsAlone,
                            viewModel = detailVm,
                        )
                    }
                    is WorkspaceDetailPane.Create -> {
                        val createVm: CreateMaskedEmailViewModel = hiltViewModel(key = "ws_create")
                        CreateMaskedEmailScreen(
                            onNavigateBack = {
                                createVm.resetForm()
                                workspaceViewModel.clearDetail()
                                scope.launch {
                                    if (navigator.canNavigateBack()) navigator.navigateBack()
                                }
                            },
                            onCreated = { email ->
                                createVm.resetForm()
                                workspaceViewModel.onMaskCreated(id = "", email = email)
                            },
                            onCreatedWithId = { id, email ->
                                createVm.resetForm()
                                workspaceViewModel.onMaskCreated(id = id, email = email)
                            },
                            onSignInFromBanner = onSignInFromBanner,
                            onReauthenticate = onReauthenticate,
                            showDemoBanner = detailStandsAlone,
                            viewModel = createVm,
                        )
                    }
                }
            }
        },
    )
}

/**
 * Calls [onLeft] when the navigator moves away from [detailRole] (toolbar, system, or predictive
 * gesture back). Only an actual Detail -> elsewhere transition counts, so restored state is never
 * cleared before it opens.
 */
@Composable
internal fun ForgetSelectionWhenPaneLeft(
    navigator: ThreePaneScaffoldNavigator<String>,
    detailRole: ThreePaneScaffoldRole,
    onLeft: () -> Unit,
) {
    val currentOnLeft by rememberUpdatedState(onLeft)
    LaunchedEffect(navigator) {
        var previous = navigator.currentDestination?.pane
        snapshotFlow { navigator.currentDestination?.pane }.collect { pane ->
            if (previous == detailRole && pane != detailRole) currentOnLeft()
            previous = pane
        }
    }
}

@Composable
internal fun paneDragHandleIfFlat(
    adaptiveInfo: FastMaskAdaptiveInfo,
): (@Composable ThreePaneScaffoldScope.(PaneExpansionState) -> Unit)? =
    if (!adaptiveInfo.posture.hasSeparatingVerticalHinge) {
        { state ->
            VerticalDragHandle(
                modifier = Modifier.paneExpansionDraggable(
                    state = state,
                    minTouchTargetSize = LocalMinimumInteractiveComponentSize.current,
                    interactionSource = remember { MutableInteractionSource() },
                ),
            )
        }
    } else {
        null
    }

@Composable
fun MaskDetailEmptyPlaceholder(modifier: Modifier = Modifier) {
    val shapes = remember {
        listOf(
            MaterialShapes.Cookie9Sided,
            MaterialShapes.Clover4Leaf,
            MaterialShapes.Sunny,
            MaterialShapes.Cookie6Sided,
        )
    }
    Surface(
        modifier = modifier.fillMaxSize().testTag("mask_detail_empty_placeholder"),
        color = MaterialTheme.colorScheme.surface,
    ) {
        Box(
            modifier = Modifier.fillMaxSize().padding(32.dp),
            contentAlignment = Alignment.Center,
        ) {
            Column(
                modifier = Modifier.widthIn(max = 380.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                MorphingShape(
                    polygons = shapes,
                    color = MaterialTheme.colorScheme.primaryContainer,
                    rotationStepDegrees = 45f,
                    modifier = Modifier.size(96.dp).clearAndSetSemantics { },
                ) {
                    Icon(
                        imageVector = Icons.Filled.AlternateEmail,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.size(36.dp),
                    )
                }
                Spacer(Modifier.height(20.dp))
                Text(
                    text = stringResource(R.string.workspace_empty_title),
                    style = MaterialTheme.typography.headlineSmallEmphasized,
                    color = MaterialTheme.colorScheme.onSurface,
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    text = stringResource(R.string.workspace_empty_body),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(16.dp))
                Text(
                    text = stringResource(R.string.workspace_shortcuts_hint),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.outline,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}

/**
 * Hands the workspace's selected email ID to the detail ViewModel once per change.
 */
internal class DetailSelection {
    private var id: String? = null

    fun follow(viewModel: MaskedEmailDetailViewModel, emailId: String) {
        if (emailId == this.id) return
        this.id = emailId
        viewModel.openEmail(emailId)
    }
}

/**
 * The detail pane keeps its back arrow only when it is the single visible pane (medium single-pane
 * workspace). Shown beside the list it needs no back arrow because the list pane is already visible.
 */
internal fun detailIsOnlyVisiblePane(
    value: ThreePaneScaffoldValue,
    detailRole: ThreePaneScaffoldRole,
): Boolean =
    value[detailRole] == PaneAdaptedValue.Expanded &&
        listOf(
            ListDetailPaneScaffoldRole.List,
            ListDetailPaneScaffoldRole.Detail,
            ListDetailPaneScaffoldRole.Extra,
        ).count { value[it] == PaneAdaptedValue.Expanded } == 1
