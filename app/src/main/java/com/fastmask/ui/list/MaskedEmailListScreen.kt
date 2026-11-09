package com.fastmask.ui.list

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.text
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import com.fastmask.R
import com.fastmask.domain.model.EmailState
import com.fastmask.domain.model.MaskedEmail
import com.fastmask.ui.accessibility.politeLiveRegion
import com.fastmask.ui.accessibility.screenHeading
import com.fastmask.ui.common.ContentWidth
import com.fastmask.core.platform.copyToClipboard
import com.fastmask.ui.common.isCompactHeight
import com.fastmask.ui.common.readableWidth
import com.fastmask.ui.components.DemoBanner
import com.fastmask.ui.components.ErrorState
import com.fastmask.ui.components.InlineErrorBanner
import com.fastmask.ui.components.TutorialOverlay
import com.fastmask.ui.util.RelativeTime
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun MaskedEmailListScreen(
    onNavigateToCreate: () -> Unit,
    onNavigateToDetail: (String) -> Unit,
    onNavigateToSettings: () -> Unit,
    onSignInFromBanner: () -> Unit,
    onReauthenticate: () -> Unit,
    pendingResult: ListResult? = null,
    onResultConsumed: (ListResult) -> Unit = {},
    viewModel: MaskedEmailListViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()
    val listState = rememberLazyListState()
    val haptic = LocalHapticFeedback.current
    val context = LocalContext.current
    val resources = LocalResources.current
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    val copiedFormat = stringResource(R.string.list_copied_value)
    val archivedMessage = stringResource(R.string.list_archived_snackbar)
    val undoLabel = stringResource(R.string.list_undo)

    // Latch the id into local state BEFORE consuming: consuming writes null into
    // the SavedStateHandle, and a LaunchedEffect keyed on the consumed value would
    // be cancelled mid-showSnackbar, dismissing it after one frame.
    var pendingUndo by rememberSaveable { mutableStateOf<ListResult.Archived?>(null) }
    var handledUndo by rememberSaveable { mutableStateOf<ListResult.Archived?>(null) }
    LaunchedEffect(pendingResult, pendingUndo) {
        if (pendingResult is ListResult.Archived && pendingUndo == null && pendingResult != handledUndo) {
            pendingUndo = pendingResult
        }
    }
    LaunchedEffect(pendingUndo) {
        val undo = pendingUndo ?: return@LaunchedEffect
        // Long, not Short: this snackbar is an undo affordance — 4 s is a tight
        // window to notice it mid screen transition and reach the action.
        val result = snackbarHostState.showSnackbar(
            message = archivedMessage,
            actionLabel = undoLabel,
            duration = SnackbarDuration.Long,
        )
        if (result == SnackbarResult.ActionPerformed) {
            viewModel.restoreMask(undo.id, undo.previousState)
        }
        // Consume navigation state only after the snackbar produced a result.
        // A recreation cancels this effect, so the saved id remains available
        // and rememberSaveable restarts the Undo instead of losing it.
        handledUndo = undo
        onResultConsumed(undo)
        pendingUndo = null
    }

    val copyAction = stringResource(R.string.create_email_copy_action)
    var pendingCreated by remember { mutableStateOf<String?>(null) }
    var highlightEmail by remember { mutableStateOf<String?>(null) }
    fun announceCreated(email: String) {
        pendingCreated = email
        highlightEmail = email
    }
    LaunchedEffect(pendingResult) {
        if (pendingResult is ListResult.Created && pendingCreated == null) {
            announceCreated(pendingResult.email)
            onResultConsumed(pendingResult)
        }
    }
    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                is MaskedEmailListEvent.RandomCreated -> announceCreated(event.email)
                is MaskedEmailListEvent.RandomFailed -> {
                    snackbarHostState.showSnackbar(
                        message = resources.getString(event.messageRes),
                        duration = SnackbarDuration.Short,
                    )
                }
            }
        }
    }
    // stringResource() is composable: resolve it here, not inside the LaunchedEffect.
    val createdMessage = pendingCreated?.let { email ->
        stringResource(R.string.create_email_created, email)
    }
    LaunchedEffect(pendingCreated) {
        val email = pendingCreated ?: return@LaunchedEffect
        val message = createdMessage ?: return@LaunchedEffect
        val result = snackbarHostState.showSnackbar(
            message = message,
            actionLabel = copyAction,
            duration = SnackbarDuration.Long,
        )
        if (result == SnackbarResult.ActionPerformed) {
            copyToClipboard(context, email)
        }
        pendingCreated = null
    }
    LaunchedEffect(highlightEmail, uiState.emails) {
        val target = highlightEmail ?: return@LaunchedEffect
        if (uiState.emails.any { it.email == target }) {
            listState.animateScrollToItem(0)
            delay(HIGHLIGHT_HOLD_MS)
            highlightEmail = null
        }
    }

    LaunchedEffect(highlightEmail) {
        if (highlightEmail == null) return@LaunchedEffect
        delay(HIGHLIGHT_GIVE_UP_MS)
        highlightEmail = null
    }

    val session by viewModel.session.collectAsState()
    val tutorialCompleted by viewModel.tutorialCompleted.collectAsState()

    // Lambdas created inside itemsIndexed capture the row's `email`, so each
    // recomposition (every search keystroke) hands every MaskRow a fresh pair and
    // defeats skipping. Take the mask as a parameter and remember one pair.
    val currentOnNavigateToDetail by rememberUpdatedState(onNavigateToDetail)
    val onRowOpen = remember {
        { email: MaskedEmail -> currentOnNavigateToDetail(email.id) }
    }
    val onRowCopy = remember(copiedFormat) {
        { email: MaskedEmail ->
            copyToClipboard(context, email.email)
            haptic.performHapticFeedback(HapticFeedbackType.Confirm)
            scope.launch {
                snackbarHostState.showSnackbar(
                    copiedFormat.format(email.email),
                    duration = SnackbarDuration.Short,
                )
            }
            Unit
        }
    }

    val lifecycleOwner = LocalLifecycleOwner.current
    LaunchedEffect(lifecycleOwner) {
        lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            delay(RESUME_REFRESH_DELAY_MS)
            viewModel.refreshIfStale()
        }
    }

    val tutorialBounds = remember { mutableStateMapOf<TutorialTarget, Rect>() }
    var showTutorial by remember { mutableStateOf(false) }

    val shouldShow = session.isDemo && !tutorialCompleted &&
        TutorialTarget.entries.all { it in tutorialBounds }
    val finishTutorial = {
        viewModel.markTutorialCompleted()
        showTutorial = false
    }
    LaunchedEffect(session, tutorialCompleted, shouldShow) {
        showTutorial = shouldShow
    }

    Box(modifier = Modifier.fillMaxSize()) {
        MaskedEmailListContent(
            uiState = uiState,
            snackbarHostState = snackbarHostState,
            listState = listState,
            highlightEmail = highlightEmail,
            onSearchQueryChange = viewModel::onSearchQueryChange,
            onFilterSelect = {
                haptic.performHapticFeedback(HapticFeedbackType.ToggleOn)
                viewModel.onFilterChange(it)
            },
            onRowOpen = onRowOpen,
            onRowCopy = onRowCopy,
            onReauthenticate = onReauthenticate,
            onRefresh = viewModel::loadMaskedEmails,
            onNavigateToCreate = onNavigateToCreate,
            onRandomMask = viewModel::createRandomMask,
            onNavigateToSettings = onNavigateToSettings,
            onTutorialBounds = { target, bounds -> tutorialBounds[target] = bounds },
            demoBanner = { DemoBanner(onSignInClick = onSignInFromBanner, modifier = Modifier.padding(horizontal = ListGutter)) },
            // The scrim blocks touch but not screen readers: hide the subtree from
            // TalkBack while the tutorial is up.
            modifier = if (showTutorial) Modifier.clearAndSetSemantics { } else Modifier,
        )

        TutorialOverlay(
            visible = showTutorial,
            steps = rememberTutorialSteps(bounds = tutorialBounds),
            onComplete = finishTutorial,
            onSkip = finishTutorial,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun MaskedEmailListContent(
    uiState: MaskedEmailListUiState,
    snackbarHostState: SnackbarHostState,
    listState: LazyListState,
    highlightEmail: String?,
    onSearchQueryChange: (String) -> Unit,
    onFilterSelect: (EmailFilter) -> Unit,
    onRowOpen: (MaskedEmail) -> Unit,
    onRowCopy: (MaskedEmail) -> Unit,
    onReauthenticate: () -> Unit,
    onRefresh: () -> Unit,
    onNavigateToCreate: () -> Unit,
    onRandomMask: () -> Unit,
    onNavigateToSettings: () -> Unit,
    onTutorialBounds: (TutorialTarget, Rect) -> Unit,
    demoBanner: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    initialMenuExpanded: Boolean = false,
) {
    val context = LocalContext.current
    // Phones in landscape: a large collapsing bar plus pinned search and filters
    // left ~30% of the height for the list. There the bar is the small one and
    // search + filters scroll away with the list.
    val compactHeight = isCompactHeight()
    val scrollBehavior = if (compactHeight) {
        TopAppBarDefaults.pinnedScrollBehavior()
    } else {
        TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    }
    val headerState = remember { CollapsingHeaderState() }

    val totalCount = uiState.emails.size

    val filterCounts = remember(uiState.emails) { EmailFilter.countsOf(uiState.emails) }
    val activeCount = filterCounts[EmailFilter.ENABLED] ?: 0

    var menuExpanded by rememberSaveable { mutableStateOf(initialMenuExpanded) }
    BackHandler(enabled = menuExpanded) { menuExpanded = false }

    val fabScroll = remember { FabScrollState() }
    val atTop by remember { derivedStateOf { !listState.canScrollBackward } }
    val fabExtended = atTop || fabScroll.scrollingUp

    Scaffold(
        modifier = modifier
            .nestedScroll(scrollBehavior.nestedScrollConnection)
            .nestedScroll(fabScroll.connection)
            .then(if (compactHeight) Modifier.nestedScroll(headerState.connection) else Modifier),
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            val barTitle: @Composable () -> Unit = {
                Text(
                    text = stringResource(R.string.email_list_title),
                    style = if (compactHeight) {
                        MaterialTheme.typography.titleLargeEmphasized
                    } else {
                        MaterialTheme.typography.displaySmallEmphasized
                    },
                    modifier = Modifier.screenHeading(),
                )
            }
            // Two pluralized fragments joined by a translatable format, not one string
            // with two %d: Polish, Russian, Ukrainian and Arabic inflect the noun by
            // count, and the separator in the resource lets RTL locales reorder them.
            val barSubtitle: @Composable () -> Unit = {
                Text(
                    text = stringResource(
                        R.string.list_stats,
                        pluralStringResource(R.plurals.list_stats_active, activeCount, activeCount),
                        pluralStringResource(R.plurals.list_stats_total, totalCount, totalCount),
                    ),
                )
            }
            val barActions: @Composable RowScope.() -> Unit = {
                IconButton(onClick = onNavigateToSettings) {
                    Icon(
                        imageVector = Icons.Filled.Settings,
                        contentDescription = stringResource(R.string.email_list_settings),
                    )
                }
            }
            val barColors = TopAppBarDefaults.topAppBarColors(
                containerColor = MaterialTheme.colorScheme.background,
                scrolledContainerColor = MaterialTheme.colorScheme.surfaceContainer,
            )
            if (compactHeight) {
                TopAppBar(
                    title = barTitle,
                    subtitle = barSubtitle,
                    actions = barActions,
                    colors = barColors,
                    scrollBehavior = scrollBehavior,
                )
            } else {
                LargeFlexibleTopAppBar(
                    title = barTitle,
                    subtitle = barSubtitle,
                    actions = barActions,
                    colors = barColors,
                    scrollBehavior = scrollBehavior,
                )
            }
        },
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
        floatingActionButton = {
            CreateFabMenu(
                expanded = menuExpanded,
                onExpandedChange = { menuExpanded = it },
                extended = fabExtended,
                isCreating = uiState.isCreatingRandom,
                onNewMask = {
                    menuExpanded = false
                    onNavigateToCreate()
                },
                onRandomMask = {
                    menuExpanded = false
                    onRandomMask()
                },
                modifier = Modifier.onGloballyPositioned { coords ->
                    onTutorialBounds(TutorialTarget.CREATE_FAB, coords.boundsInRoot())
                },
            )
        },
    ) { paddingValues ->
        Box(modifier = Modifier.fillMaxSize().padding(paddingValues)) {
            Column(modifier = Modifier.fillMaxSize()) {
                // The list is a cached snapshot — say so. Showing masks that
                // may since have been deleted or renamed without marking them
                // stale would quietly misinform.
                uiState.cachedAt?.let { cachedAt ->
                    OfflineBanner(
                        text = stringResource(
                            R.string.list_offline_cached,
                            RelativeTime.format(context, cachedAt),
                        ),
                        modifier = Modifier.readableWidth(ContentWidth.List),
                    )
                }

                // A manual refresh can fail while the previous good list is
                // still usable. Keep the data on screen, but do not turn the
                // failed request into a silent no-op.
                uiState.inlineError?.let { error ->
                    InlineErrorBanner(
                        error = error,
                        onRetry = onRefresh,
                        onReauthenticate = onReauthenticate,
                        modifier = Modifier
                            .readableWidth(ContentWidth.List)
                            .padding(horizontal = ListGutter, vertical = 4.dp),
                    )
                }

                // Once the app bar collapses, this block takes the bar's scrolled
                // colour and real breathing room, so bar + search + filters read as
                // one header. In landscape the whole block scrolls away with the list.
                val headerColor = if (compactHeight) {
                    MaterialTheme.colorScheme.background
                } else {
                    lerp(
                        MaterialTheme.colorScheme.background,
                        MaterialTheme.colorScheme.surfaceContainer,
                        scrollBehavior.state.collapsedFraction,
                    )
                }
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .then(if (compactHeight) with(headerState) { Modifier.collapsing() }
                        else Modifier)
                        .then(if (compactHeight) Modifier.draggable(headerState.dragState, Orientation.Vertical) else Modifier)
                        .drawBehind { drawRect(headerColor) }
                        .padding(top = 12.dp, bottom = 12.dp)
                        .readableWidth(ContentWidth.List),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    val gutterPx = with(LocalDensity.current) { ListGutter.toPx() }
                    SearchField(
                        query = uiState.searchQuery,
                        onQueryChange = onSearchQueryChange,
                        modifier = Modifier.padding(horizontal = ListGutter).onGloballyPositioned { coords ->
                            onTutorialBounds(TutorialTarget.SEARCH, coords.boundsInRoot())
                        },
                    )

                    FilterGroup(
                        selected = uiState.selectedFilter,
                        counts = filterCounts,
                        onSelect = onFilterSelect,
                        modifier = Modifier.onGloballyPositioned { coords ->
                            val full = coords.boundsInRoot()
                            onTutorialBounds(TutorialTarget.FILTER, full.copy(left = full.left + gutterPx, right = full.right - gutterPx))
                        },
                    )

                    // Live region for TalkBack: announces the hit count while searching. Without a
                    // query the filter buttons already carry the counts, so it stays out of the way.
                    if (uiState.emails.isNotEmpty() && uiState.searchQuery.isNotBlank()) {
                        Text(
                            text = stringResource(
                                R.string.email_list_result_count,
                                uiState.filteredEmails.size,
                                uiState.emails.size,
                            ),
                            modifier = Modifier.fillMaxWidth().padding(horizontal = ListGutter).politeLiveRegion(),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.End,
                        )
                    }
                }

                when {
                    uiState.isLoading && uiState.emails.isEmpty() -> {
                        Column(Modifier.fillMaxSize()) {
                            demoBanner()
                            LoadingShimmer(
                                loadingDescription = stringResource(R.string.state_working),
                            )
                        }
                    }
                    uiState.error != null && uiState.emails.isEmpty() -> {
                        val error = uiState.error
                        Column(Modifier.fillMaxSize()) {
                            demoBanner()
                            ErrorState(
                                title = R.string.error_load_emails,
                                error = error,
                                onRetry = onRefresh,
                                onReauthenticate = onReauthenticate,
                            )
                        }
                    }
                    uiState.filteredEmails.isEmpty() -> {
                        // Masks exist but none match the search/filter → don't
                        // show the "create your first mask" empty state, which
                        // wrongly implies the account is empty.
                        Column(Modifier.fillMaxSize()) {
                            demoBanner()
                            if (uiState.emails.isEmpty()) {
                                EmptyBlock(onCreate = onNavigateToCreate)
                            } else {
                                NoMatchesBlock(
                                    hasQuery = uiState.searchQuery.isNotEmpty(),
                                    onClearSearch = { onSearchQueryChange("") },
                                )
                            }
                        }
                    }
                    else -> {
                        MaskList(
                            uiState = uiState,
                            listState = listState,
                            highlightEmail = highlightEmail,
                            onRefresh = onRefresh,
                            onRowOpen = onRowOpen,
                            onRowCopy = onRowCopy,
                            onTutorialBounds = onTutorialBounds,
                            demoBanner = demoBanner,
                        )
                    }
                }
            }

            // Tap-outside target behind the open FAB menu. M3 FAB menus draw no scrim, so it is
            // invisible; it only closes the menu.
            val closeMenuLabel = stringResource(R.string.list_fab_close_menu)
            if (menuExpanded) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .clickable(
                            interactionSource = null,
                            indication = null,
                            onClickLabel = closeMenuLabel,
                            role = Role.Button,
                        ) { menuExpanded = false },
                )
            }
        }
    }
}

/** Settle margin so the on-resume refetch does not start mid screen transition. */
private const val RESUME_REFRESH_DELAY_MS = 250L

private const val HIGHLIGHT_GIVE_UP_MS = 10_000L
