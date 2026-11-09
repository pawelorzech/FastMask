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
    /** What the detail or create screen handed back; null when nothing is waiting. */
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

    // Names the address that was copied, so similar masks can be told apart.
    val copiedFormat = stringResource(R.string.list_copied_value)
    val archivedMessage = stringResource(R.string.list_archived_snackbar)
    val undoLabel = stringResource(R.string.list_undo)

    // A mask was just archived on the detail screen; offer an Undo snackbar here,
    // where the mask reappears. The id must be latched into local state BEFORE
    // consuming: consuming writes null into the SavedStateHandle, which
    // recomposes, and a LaunchedEffect keyed directly on the consumed value
    // would be cancelled mid-showSnackbar — dismissing the snackbar after a
    // single frame and making Undo untappable.
    //
    // Guard pendingUndo != null: a rapid second archive of another mask does
    // not overwrite the first undo — the user only gets one undo at a time.
    // The result just shown is remembered too: consuming it reaches pendingResult
    // a frame after pendingUndo clears, and in that frame the latch below would
    // otherwise take the same result again and show the snackbar forever.
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

    // A mask was just created; confirm it here, where it now appears, with the
    // Copy action. Latched before
    // consuming for the same reason as the undo snackbar above: consuming
    // writes null, which would otherwise cancel this effect mid-snackbar.
    val copyAction = stringResource(R.string.create_email_copy_action)
    var pendingCreated by remember { mutableStateOf<String?>(null) }
    // The address that gets the brief "just created" highlight once it shows up
    // in the list (the list reloads on resume / after a random mask).
    var highlightEmail by remember { mutableStateOf<String?>(null) }
    // One path for both ways a mask gets created: confirm it, and highlight it.
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
    // The FAB menu's "Random mask" reports back through the same confirmation
    // snackbar (with its Copy action); a failure gets a plain snackbar.
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
    // stringResource() is composable, so the formatted message is resolved
    // here in the composable body — not inside the LaunchedEffect below,
    // which runs in a plain coroutine and can't call it.
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
    // Once the new mask is in the list: bring it into view, hold the highlight
    // a moment, then let it fade (the row animates its own alpha).
    LaunchedEffect(highlightEmail, uiState.emails) {
        val target = highlightEmail ?: return@LaunchedEffect
        if (uiState.emails.any { it.email == target }) {
            listState.animateScrollToItem(0)
            delay(HIGHLIGHT_HOLD_MS)
            highlightEmail = null
        }
    }

    // The row may never show (filtered out, search active, or the fetch that should
    // list it failed): a highlight left armed would flash an unrelated moment later.
    LaunchedEffect(highlightEmail) {
        if (highlightEmail == null) return@LaunchedEffect
        delay(HIGHLIGHT_GIVE_UP_MS)
        highlightEmail = null
    }

    val session by viewModel.session.collectAsState()
    val tutorialCompleted by viewModel.tutorialCompleted.collectAsState()

    // One stable callback pair for ALL rows. Lambdas created inside
    // itemsIndexed capture their row's `email`, so every recomposition of this
    // screen — every search keystroke, every filter tap — handed each visible
    // MaskRow a fresh lambda pair, defeated skipping, and re-ran every row's
    // composition while the user was only typing. Taking the mask as a
    // parameter and remembering the wrapper keeps the instances (and the
    // recompositions) away from the hot typing path.
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
    // Changes made inside the app reach the view model through the repository's
    // `changes` signal. This resume refresh is for everything else (another
    // device, a long time in the background); the view model skips it when a
    // fetch began less than 30 s ago, which also spares cold start a second fetch.
    LaunchedEffect(lifecycleOwner) {
        lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            delay(RESUME_REFRESH_DELAY_MS)
            viewModel.refreshIfStale()
        }
    }

    val tutorialBounds = remember { mutableStateMapOf<TutorialTarget, Rect>() }
    var showTutorial by remember { mutableStateOf(false) }

    // Show the tutorial once every target has measured its bounds (only in demo
    // mode, and only until it is completed or skipped). A target that never
    // lays out keeps the tutorial from appearing.
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
            demoBanner = { DemoBanner(onSignInClick = onSignInFromBanner) },
            // While the tutorial is up, drop this whole subtree out of the
            // semantics tree. The scrim already blocks touch; without this a
            // screen reader still walked the mask list, the search field and
            // the filter buttons underneath a 70% black overlay, reading out an
            // interface the user cannot see or reach.
            modifier = if (showTutorial) Modifier.clearAndSetSemantics { } else Modifier,
        )

        // Tutorial coach marks — overlays the whole screen (app bar and FAB
        // included, so the spotlight can point at them), and while it is up the
        // content beneath is hidden from screen readers as well as from touch
        // (see TutorialOverlay).
        TutorialOverlay(
            visible = showTutorial,
            steps = rememberTutorialSteps(bounds = tutorialBounds),
            onComplete = finishTutorial,
            onSkip = finishTutorial,
        )
    }
}

/**
 * The stateless list screen: everything the user sees, driven by [uiState] and
 * callbacks, so it can be previewed without Hilt.
 */
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

    // Per-filter counts only change with the data. Computing them in a single
    // remembered pass avoids iterating the whole list on every recomposition
    // of this screen (including every search keystroke) for numbers that
    // only move when `uiState.emails` changes.
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
            // Two pluralized fragments joined by a translatable
            // format, rather than one string with two %d.
            // Polish, Russian, Ukrainian and Arabic inflect the
            // noun by count ("1 maska / 2 maski / 5 masek"),
            // which a single %d format cannot express; keeping
            // the separator in the resource lets RTL locales
            // reorder the fragments.
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

                // No horizontal padding on the Column itself: the filter row owns
                // its 16dp gutters so it can scroll edge to edge.
                // Once the app bar collapses, this block takes the bar's scrolled
                // colour and real breathing room, so bar + search + filters read as
                // one header instead of a search pill glued under a grey strip.
                // In landscape the whole block scrolls away with the list instead.
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
                            // The row is full-bleed; the tutorial spotlight keeps the
                            // gutter-inset bounds the other steps use.
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

/**
 * Waits before the on-resume refresh so a refetch does not start in the middle
 * of the screen transition that brought the list back (added in v1.3 against the
 * then 220 ms slide; navigation is spring-driven now, so this is a settle margin,
 * not a measured duration).
 */
private const val RESUME_REFRESH_DELAY_MS = 250L

/** How long a "just created" highlight waits for its row before giving up. */
private const val HIGHLIGHT_GIVE_UP_MS = 10_000L
