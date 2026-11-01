package com.fastmask.ui.list

import androidx.compose.ui.draw.drawBehind

import androidx.compose.ui.graphics.lerp

import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.semantics.text
import androidx.compose.ui.semantics.getTextLayoutResult
import androidx.compose.ui.text.TextLayoutResult

import android.content.res.Configuration
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Casino
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Inbox
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SearchOff
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ButtonGroupDefaults
import androidx.compose.material3.ContainedLoadingIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FloatingActionButtonMenu
import androidx.compose.material3.FloatingActionButtonMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.ToggleFloatingActionButton
import androidx.compose.material3.ToggleFloatingActionButtonDefaults
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.graphics.SolidColor
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import com.fastmask.R
import com.fastmask.ui.common.ContentWidth
import com.fastmask.ui.common.isCompactHeight
import com.fastmask.ui.common.readableWidth
import com.fastmask.domain.model.AppMode
import com.fastmask.domain.model.EmailState
import com.fastmask.domain.model.MaskedEmail
import com.fastmask.ui.accessibility.politeLiveRegion
import com.fastmask.ui.common.copyToClipboard
import com.fastmask.ui.components.DemoBanner
import com.fastmask.ui.components.DemoBannerContent
import com.fastmask.ui.components.TooltipPosition
import com.fastmask.ui.components.TutorialOverlay
import com.fastmask.ui.components.TutorialStep
import com.fastmask.ui.components.expressive.MaskAvatar
import com.fastmask.ui.components.expressive.SectionHeader
import com.fastmask.ui.components.expressive.SegmentedGap
import com.fastmask.ui.components.expressive.SegmentedRow
import com.fastmask.ui.components.expressive.StateChip
import com.fastmask.ui.components.expressive.segmentedShape
import com.fastmask.ui.navigation.maskContainer
import com.fastmask.ui.theme.FastMaskTheme
import com.fastmask.ui.util.RelativeTime
import java.time.Instant
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private data class PendingArchiveUndo(
    val id: String,
    val previousState: EmailState,
)

/** Saves the archive id and its exact pre-archive state as one atomic value. */
private val PendingArchiveUndoStateSaver = Saver<MutableState<PendingArchiveUndo?>, List<String>>(
    save = { state ->
        state.value?.let { undo -> listOf(undo.id, undo.previousState.name) }.orEmpty()
    },
    restore = { saved ->
        mutableStateOf(
            saved.takeIf { it.size == 2 }?.let { values ->
                runCatching {
                    PendingArchiveUndo(values[0], EmailState.valueOf(values[1]))
                }.getOrNull()
            },
        )
    },
)

@Composable
fun MaskedEmailListScreen(
    onNavigateToCreate: () -> Unit,
    onNavigateToDetail: (String) -> Unit,
    onNavigateToSettings: () -> Unit,
    onSignInFromBanner: () -> Unit,
    onReauthenticate: () -> Unit,
    justArchivedId: String? = null,
    justArchivedState: EmailState? = null,
    onArchivedConsumed: (String) -> Unit = {},
    justCreatedEmail: String? = null,
    onCreatedConsumed: () -> Unit = {},
    viewModel: MaskedEmailListViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()
    val listState = rememberLazyListState()
    val haptic = LocalHapticFeedback.current
    val context = LocalContext.current
    val resources = LocalResources.current
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    // Names the address that was copied. With several similar masks the old
    // "Copied to clipboard" left the user pasting somewhere just to check
    // which one they got — on the most frequent action in the app.
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
    var pendingUndo by rememberSaveable(saver = PendingArchiveUndoStateSaver) {
        mutableStateOf(null)
    }
    LaunchedEffect(justArchivedId, justArchivedState, pendingUndo) {
        if (justArchivedId != null && justArchivedState != null && pendingUndo == null) {
            pendingUndo = PendingArchiveUndo(
                id = justArchivedId,
                previousState = justArchivedState,
            )
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
        onArchivedConsumed(undo.id)
        pendingUndo = null
    }

    // A mask was just created; confirm it here, where it now appears, with the
    // same Copy action the create screen used to offer. Latched before
    // consuming for the same reason as the undo snackbar above: consuming
    // writes null, which would otherwise cancel this effect mid-snackbar.
    val copyAction = stringResource(R.string.create_email_copy_action)
    var pendingCreated by remember { mutableStateOf<String?>(null) }
    // The address that gets the brief "just created" highlight once it shows up
    // in the list (the list reloads on resume / after a random mask).
    var highlightEmail by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(justCreatedEmail) {
        if (justCreatedEmail != null && pendingCreated == null) {
            pendingCreated = justCreatedEmail
            highlightEmail = justCreatedEmail
            onCreatedConsumed()
        }
    }
    // The FAB menu's "Random mask" reports back through the same confirmation
    // snackbar (with its Copy action); a failure gets a plain snackbar.
    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                is MaskedEmailListEvent.RandomCreated -> {
                    pendingCreated = event.email
                    highlightEmail = event.email
                }
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
            delay(1_600L)
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

    val appMode by viewModel.appMode.collectAsState()
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
    // Deliberately starts at 0: the resume refresh is not only a freshness
    // nicety — it is how this screen learns about masks created or archived
    // on other screens (those mutations do not write back into this list).
    // Anchoring at "now" skipped the refetch after every return from
    // create/detail, leaving stale counts and rows. The 30 s throttle only
    // guards rapid background/foreground toggles while staying composed.
    var lastRefreshTime by remember { mutableLongStateOf(0L) }
    LaunchedEffect(lifecycleOwner) {
        lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            delay(250L)
            val now = System.currentTimeMillis()
            if (now - lastRefreshTime > 30_000L) {
                lastRefreshTime = now
                viewModel.refreshMaskedEmails()
            }
        }
    }

    // Tutorial coach-mark bounds. Each key corresponds to a step index defined
    // below; values are filled in via Modifier.onGloballyPositioned on the
    // respective UI elements as they enter composition.
    val tutorialBounds = remember { mutableStateMapOf<Int, Rect>() }
    var showTutorial by remember { mutableStateOf(false) }

    // Show the tutorial once all 5 target elements have measured their bounds
    // (only relevant in demo mode and only once per demo session). A timeout
    // ensures the tutorial eventually appears even if a view fails to lay out.
    val shouldShow = appMode == AppMode.DEMO && !tutorialCompleted && tutorialBounds.size >= 5
    LaunchedEffect(appMode, tutorialCompleted, shouldShow) {
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
            onRetry = viewModel::loadMaskedEmails,
            onReauthenticate = onReauthenticate,
            onRefresh = viewModel::loadMaskedEmails,
            onNavigateToCreate = onNavigateToCreate,
            onRandomMask = viewModel::createRandomMask,
            onNavigateToSettings = onNavigateToSettings,
            onTutorialBounds = { index, bounds -> tutorialBounds[index] = bounds },
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
            onComplete = {
                viewModel.markTutorialCompleted()
                showTutorial = false
            },
            onSkip = {
                viewModel.markTutorialCompleted()
                showTutorial = false
            },
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
    onRetry: () -> Unit,
    onReauthenticate: () -> Unit,
    onRefresh: () -> Unit,
    onNavigateToCreate: () -> Unit,
    onRandomMask: () -> Unit,
    onNavigateToSettings: () -> Unit,
    onTutorialBounds: (Int, Rect) -> Unit,
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

    val activeCount = uiState.emails.count { it.state == EmailState.ENABLED || it.state == EmailState.PENDING }
    val totalCount = uiState.emails.size

    // Per-filter counts only change with the data. Computing them in plain
    // composition meant four passes over the whole list on EVERY recomposition
    // of this screen — including every search keystroke — for numbers that
    // almost never move.
    val filterCounts = remember(uiState.emails) {
        mapOf(
            EmailFilter.ALL to uiState.emails.size,
            EmailFilter.ENABLED to activeCount,
            EmailFilter.DISABLED to uiState.emails.count { it.state == EmailState.DISABLED },
            EmailFilter.DELETED to uiState.emails.count { it.state == EmailState.DELETED },
        )
    }

    var menuExpanded by rememberSaveable { mutableStateOf(initialMenuExpanded) }
    BackHandler(enabled = menuExpanded) { menuExpanded = false }

    // The FAB shows its label at the top of the list and while the user scrolls
    // up; it collapses to the icon while they scroll down.
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
                    modifier = Modifier.semantics { heading() },
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
                    onTutorialBounds(4, coords.boundsInRoot())
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
                uiState.inlineErrorRes?.let { errorRes ->
                    val action = listErrorActionFor(errorRes)
                    InlineErrorBanner(
                        text = stringResource(errorRes),
                        actionLabel = stringResource(
                            when (action) {
                                ListErrorAction.RETRY -> R.string.error_retry
                                ListErrorAction.REAUTHENTICATE ->
                                    R.string.settings_signin_with_fastmail
                            },
                        ),
                        onAction = when (action) {
                            ListErrorAction.RETRY -> onRetry
                            ListErrorAction.REAUTHENTICATE -> onReauthenticate
                        },
                        modifier = Modifier.readableWidth(ContentWidth.List),
                    )
                }

                // Search + filters stay pinned under the (collapsing) app bar
                // while the list scrolls beneath them.
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
                    val gutterPx = with(LocalDensity.current) { FilterGutter.toPx() }
                    SearchField(
                        query = uiState.searchQuery,
                        onQueryChange = onSearchQueryChange,
                        modifier = Modifier.padding(horizontal = FilterGutter).onGloballyPositioned { coords ->
                            onTutorialBounds(1, coords.boundsInRoot())
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
                            onTutorialBounds(2, full.copy(left = full.left + gutterPx, right = full.right - gutterPx))
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
                            modifier = Modifier.fillMaxWidth().padding(horizontal = FilterGutter).politeLiveRegion(),
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
                    uiState.errorRes != null && uiState.emails.isEmpty() -> {
                        val errorRes = uiState.errorRes ?: R.string.error_load_emails
                        val action = listErrorActionFor(errorRes)
                        Column(Modifier.fillMaxSize()) {
                            demoBanner()
                            ErrorBlock(
                                message = stringResource(errorRes),
                                actionLabel = stringResource(
                                    when (action) {
                                        ListErrorAction.RETRY -> R.string.error_retry
                                        ListErrorAction.REAUTHENTICATE ->
                                            R.string.settings_signin_with_fastmail
                                    },
                                ),
                                onAction = when (action) {
                                    ListErrorAction.RETRY -> onRetry
                                    ListErrorAction.REAUTHENTICATE -> onReauthenticate
                                },
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

/** Tracks scroll direction for the FAB without consuming any scroll. */
private class FabScrollState {
    var scrollingUp by mutableStateOf(true)
        private set

    val connection = object : NestedScrollConnection {
        override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
            if (available.y < -SCROLL_SLOP) scrollingUp = false
            else if (available.y > SCROLL_SLOP) scrollingUp = true
            return Offset.Zero
        }
    }

    private companion object {
        const val SCROLL_SLOP = 4f
    }
}

@Composable
private fun MaskList(
    uiState: MaskedEmailListUiState,
    listState: LazyListState,
    highlightEmail: String?,
    onRefresh: () -> Unit,
    onRowOpen: (MaskedEmail) -> Unit,
    onRowCopy: (MaskedEmail) -> Unit,
    onTutorialBounds: (Int, Rect) -> Unit,
    demoBanner: @Composable () -> Unit,
) {
    val nowSec = remember(uiState.emails) { Instant.now().epochSecond }
    val sections = remember(uiState.filteredEmails, nowSec) {
        groupMasksBySection(uiState.filteredEmails, nowSec)
    }
    val pullState = rememberPullToRefreshState()
    val spatial = MaterialTheme.motionScheme.defaultSpatialSpec<IntOffset>()
    val effects = MaterialTheme.motionScheme.defaultEffectsSpec<Float>()

    // Wide windows: the list keeps a readable column. The extra inset lives in the
    // LazyColumn's contentPadding (not a width cap) so the scrollable area, the
    // scrollbar and the pull-to-refresh gesture still span the whole window.
    BoxWithConstraints(Modifier.fillMaxSize()) {
    val wideInset = ((maxWidth - ContentWidth.List) / 2).coerceAtLeast(0.dp)
    PullToRefreshBox(
        isRefreshing = uiState.isLoading,
        onRefresh = onRefresh,
        state = pullState,
        modifier = Modifier.fillMaxSize(),
        indicator = {
            PullToRefreshDefaults.LoadingIndicator(
                state = pullState,
                isRefreshing = uiState.isLoading,
                modifier = Modifier.align(Alignment.TopCenter),
            )
        },
    ) {
        LazyColumn(
            state = listState,
            contentPadding = PaddingValues(start = wideInset, end = wideInset, top = 0.dp, bottom = 112.dp),
            verticalArrangement = Arrangement.spacedBy(SegmentedGap),
            modifier = Modifier
                .fillMaxSize()
                .onGloballyPositioned { coords ->
                    // Step 0 highlights the list as a whole.
                    onTutorialBounds(0, coords.boundsInRoot())
                },
        ) {
            item(key = "demo-banner") {
                // Scrolls away with the list instead of costing permanent height.
                Box(Modifier.animateItem().padding(horizontal = 16.dp)) { demoBanner() }
            }
            sections.forEachIndexed { sectionIndex, section ->
                item(key = "section-${section.section.name}") {
                    SectionHeader(
                        text = stringResource(section.section.titleRes),
                        modifier = Modifier
                            .padding(horizontal = 16.dp)
                            .animateItem(
                                fadeInSpec = effects,
                                placementSpec = spatial,
                                fadeOutSpec = effects,
                            ),
                    )
                }
                itemsIndexed(
                    items = section.items,
                    key = { _, e -> e.id },
                ) { index, email ->
                    var rowModifier = Modifier.padding(horizontal = 16.dp).animateItem(
                        fadeInSpec = effects,
                        placementSpec = spatial,
                        fadeOutSpec = effects,
                    )
                    if (sectionIndex == 0 && index == 0) {
                        rowModifier = rowModifier.onGloballyPositioned { coords ->
                            // Step 3 highlights the first mask card.
                            onTutorialBounds(3, coords.boundsInRoot())
                        }
                    }
                    MaskRow(
                        email = email,
                        index = index,
                        count = section.items.size,
                        nowSec = nowSec,
                        highlighted = highlightEmail != null && email.email == highlightEmail,
                        onOpen = onRowOpen,
                        onCopy = onRowCopy,
                        modifier = rowModifier,
                    )
                }
            }
        }
    }
    }
}

@Composable
private fun rememberTutorialSteps(bounds: Map<Int, Rect>): List<TutorialStep> {
    val titles = listOf(
        stringResource(R.string.tutorial_step_1_title),
        stringResource(R.string.tutorial_step_2_title),
        stringResource(R.string.tutorial_step_3_title),
        stringResource(R.string.tutorial_step_4_title),
        stringResource(R.string.tutorial_step_5_title),
    )
    val bodies = listOf(
        stringResource(R.string.tutorial_step_1_body),
        stringResource(R.string.tutorial_step_2_body),
        stringResource(R.string.tutorial_step_3_body),
        stringResource(R.string.tutorial_step_4_body),
        stringResource(R.string.tutorial_step_5_body),
    )
    return titles.indices.map { i ->
        TutorialStep(
            title = titles[i],
            description = bodies[i],
            targetBounds = bounds[i],
            tooltipPosition = TooltipPosition.AUTO,
        )
    }
}

/**
 * Docked search field: a pill on `surfaceContainerHigh` whose corner radius
 * tightens (spring) while it has focus. The clear button exists only when
 * there is something to clear.
 */
@Composable
private fun SearchField(
    query: String,
    onQueryChange: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val haptic = LocalHapticFeedback.current
    val focusManager = LocalFocusManager.current
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    val corner by animateDpAsState(
        targetValue = if (focused) 16.dp else 28.dp,
        animationSpec = MaterialTheme.motionScheme.fastSpatialSpec(),
        label = "searchCorner",
    )
    // Spatial springs overshoot; a negative corner radius is invalid.
    val shape = RoundedCornerShape(corner.coerceAtLeast(0.dp))
    val searchLabel = stringResource(R.string.email_list_search_placeholder)

    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .clip(shape)
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .padding(start = 16.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = Icons.Filled.Search,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.width(12.dp))
        Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
            BasicTextField(
                value = query,
                onValueChange = onQueryChange,
                singleLine = true,
                interactionSource = interaction,
                textStyle = MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.onSurface),
                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { focusManager.clearFocus() }),
                // Visual-only placeholder gives TalkBack nothing to announce for
                // this field — label it explicitly.
                modifier = Modifier
                    .fillMaxWidth()
                    .semantics { contentDescription = searchLabel },
            )
            if (query.isEmpty()) {
                Text(
                    text = searchLabel,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        AnimatedVisibility(
            visible = query.isNotEmpty(),
            enter = fadeIn(MaterialTheme.motionScheme.fastEffectsSpec()) +
                scaleIn(MaterialTheme.motionScheme.fastSpatialSpec(), initialScale = 0.6f),
            exit = fadeOut(MaterialTheme.motionScheme.fastEffectsSpec()) +
                scaleOut(MaterialTheme.motionScheme.fastSpatialSpec(), targetScale = 0.6f),
        ) {
            // IconButton is the full 48dp platform touch target.
            IconButton(
                onClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.Confirm)
                    onQueryChange("")
                },
            ) {
                Icon(
                    imageVector = Icons.Filled.Close,
                    contentDescription = stringResource(R.string.email_list_clear_search),
                )
            }
        }
    }
}

/** How long a "just created" highlight waits for its row before giving up. */
private const val HIGHLIGHT_GIVE_UP_MS = 10_000L

/** Font scale from which a mask row drops its trailing time label / state chip. */
private const val CompactRowFontScale = 1.3f

// Reuses the already-translated state labels so TalkBack announces a mask's
// state (Active / Off / Archived / Pending) that the avatar color conveys visually.
private fun stateContentDescription(state: EmailState): Int = when (state) {
    EmailState.ENABLED -> R.string.state_enabled
    EmailState.DISABLED -> R.string.state_disabled
    EmailState.DELETED -> R.string.state_deleted
    EmailState.PENDING -> R.string.state_pending
}

@Composable
internal fun MaskRow(
    email: MaskedEmail,
    index: Int,
    count: Int,
    nowSec: Long,
    highlighted: Boolean,
    onOpen: (MaskedEmail) -> Unit,
    onCopy: (MaskedEmail) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val copyLabel = stringResource(R.string.list_copy_action)
    val stateLabel = stringResource(stateContentDescription(email.state))
    // The label depends only on the timestamps and the "now" anchor — not on
    // anything else happening in this screen. Without remember, every row ran
    // a resources lookup + plural resolution on each recomposition (e.g. while
    // the user types in search).
    val timeLabel = remember(email.lastMessageAt, email.createdAt, nowSec) {
        RelativeTime.format(context, email.lastMessageAt ?: email.createdAt, nowSec)
    }
    val mutedColor = MaterialTheme.colorScheme.onSurfaceVariant
    // Wraps only before the "@" (see addressWrapText); semantics keep the plain address.
    val headline = remember(email.email, mutedColor) {
        val display = addressWrapText(email.email)
        val at = addressWrapDomainStart(display)
        buildAnnotatedString {
            if (at < 0) {
                append(display)
            } else {
                append(display.substring(0, at))
                withStyle(SpanStyle(color = mutedColor)) { append(display.substring(at)) }
            }
        }
    }
    val domain = email.forDomain?.takeIf { it.isNotBlank() }
    val note = email.description?.takeIf { it.isNotBlank() }
    // At large font scales the timestamp / state chip beside the address left it a few
    // characters ("sha…"). Past this threshold they move under the address instead.
    val compact = LocalDensity.current.fontScale >= CompactRowFontScale
    val supporting = listOfNotNull(
        note,
        domain,
        stateLabel.takeIf { compact && email.state != EmailState.ENABLED },
        timeLabel.takeIf { compact },
    ).joinToString(" · ")

    // The freshly created mask flashes a primaryContainer wash that fades out.
    val highlightAlpha by animateFloatAsState(
        targetValue = if (highlighted) 0.55f else 0f,
        animationSpec = MaterialTheme.motionScheme.slowEffectsSpec(),
        label = "justCreated",
    )
    val highlightColor = MaterialTheme.colorScheme.primaryContainer
    val restingShape = segmentedShape(index, count)

    SegmentedRow(
        index = index,
        count = count,
        onClick = { onOpen(email) },
        modifier = modifier
            .maskContainer(email.id, shape = restingShape)
            .drawWithContent {
                drawContent()
                if (highlightAlpha > 0.001f) {
                    drawOutline(
                        outline = restingShape.createOutline(size, layoutDirection, this),
                        color = highlightColor,
                        alpha = highlightAlpha,
                    )
                }
            }
            // The avatar's color is the only visual cue for the state; say it.
            .semantics { stateDescription = stateLabel },
        leading = {
            MaskAvatar(
                seedText = domain ?: email.email,
                state = email.state,
                size = 40.dp,
            )
        },
        supporting = if (supporting.isNotEmpty()) {
            {
                Text(
                    text = supporting,
                    maxLines = if (compact) 3 else 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        } else {
            null
        },
        trailing = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                // Compact rows carry both in the supporting line instead.
                if (!compact && email.state == EmailState.ENABLED) {
                    Text(
                        text = timeLabel,
                        style = MaterialTheme.typography.labelMedium,
                        color = mutedColor,
                    )
                } else if (!compact) {
                    // The row already announces its state; the chip is the
                    // sighted user's non-colour cue only.
                    Box(Modifier.clearAndSetSemantics { }) { StateChip(email.state) }
                }
                // Quick-copy: the most common action, without opening the
                // detail screen. Stock IconButton: 48dp target, press morph.
                IconButton(onClick = { onCopy(email) }) {
                    Icon(
                        imageVector = Icons.Filled.ContentCopy,
                        contentDescription = copyLabel,
                    )
                }
            }
        },
        headline = {
            // The address is the row's identity: wrap it rather than cut off the domain.
            // Semantics carry the plain address (the display text has invisible wrap hints)
            // and still expose the real layout, so accessibility and tests see what is drawn.
            var addressLayout by remember { mutableStateOf<TextLayoutResult?>(null) }
            Text(
                text = headline,
                maxLines = if (compact) 4 else 2,
                overflow = TextOverflow.Ellipsis,
                onTextLayout = { addressLayout = it },
                modifier = Modifier.clearAndSetSemantics {
                    text = AnnotatedString(email.email)
                    getTextLayoutResult { results ->
                        addressLayout?.let { results.add(it); true } ?: false
                    }
                },
            )
        },
    )
}

/** Big shape + icon used by the empty, no-match and error states. */
@Composable
private fun StateIllustration(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    modifier: Modifier = Modifier,
    container: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.secondaryContainer,
    content: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.onSecondaryContainer,
) {
    Box(
        modifier = modifier
            .size(112.dp)
            .clip(MaterialShapes.Cookie9Sided.toShape())
            .background(container)
            .clearAndSetSemantics { },
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = content,
            modifier = Modifier.size(48.dp),
        )
    }
}

@Composable
private fun EmptyBlock(onCreate: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 32.dp, vertical = 48.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        StateIllustration(icon = Icons.Filled.Inbox)
        Spacer(Modifier.size(24.dp))
        Text(
            text = stringResource(R.string.email_list_empty),
            style = MaterialTheme.typography.headlineSmallEmphasized,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.size(8.dp))
        Text(
            text = stringResource(R.string.email_list_empty_sub),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.size(24.dp))
        Button(onClick = onCreate) {
            Icon(
                imageVector = Icons.Filled.Add,
                contentDescription = null,
                modifier = Modifier.size(ButtonDefaults.IconSize),
            )
            Spacer(Modifier.width(ButtonDefaults.IconSpacing))
            Text(stringResource(R.string.email_list_create_fab))
        }
    }
}

@Composable
private fun NoMatchesBlock(hasQuery: Boolean, onClearSearch: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 32.dp, vertical = 48.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        StateIllustration(icon = Icons.Filled.SearchOff)
        Spacer(Modifier.size(24.dp))
        Text(
            text = stringResource(R.string.email_list_no_matches),
            style = MaterialTheme.typography.headlineSmallEmphasized,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.size(8.dp))
        Text(
            text = stringResource(R.string.email_list_no_matches_sub),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        if (hasQuery) {
            Spacer(Modifier.size(24.dp))
            FilledTonalButton(onClick = onClearSearch) {
                Text(stringResource(R.string.email_list_clear_search))
            }
        }
    }
}

/**
 * Initial-load state: one centered expressive loading indicator. (The name is a
 * leftover from the skeleton-card version; `AccessibilitySemanticsWiringTest`
 * pins the live-region wiring to this function.)
 */
@Composable
private fun LoadingShimmer(loadingDescription: String) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .politeLiveRegion()
            .semantics { contentDescription = loadingDescription },
        contentAlignment = Alignment.Center,
    ) {
        ContainedLoadingIndicator()
    }
}

@Composable
internal fun ErrorBlock(
    message: String,
    actionLabel: String,
    onAction: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp)
            .politeLiveRegion(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        StateIllustration(
            icon = Icons.Filled.CloudOff,
            container = MaterialTheme.colorScheme.errorContainer,
            content = MaterialTheme.colorScheme.onErrorContainer,
        )
        Spacer(Modifier.size(24.dp))
        Text(
            text = stringResource(R.string.error_load_emails),
            style = MaterialTheme.typography.headlineSmallEmphasized,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.size(8.dp))
        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.size(24.dp))
        FilledTonalButton(onClick = onAction) {
            Text(actionLabel)
        }
    }
}

/**
 * The create FAB. Collapsed it is an extended FAB with a label (at the top of
 * the list / while scrolling up) or an icon-only toggle FAB (while scrolling
 * down); tapping opens a FAB menu with "Random mask" and "New mask", and the
 * toggle morphs its + into a ×.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun CreateFabMenu(
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    extended: Boolean,
    isCreating: Boolean,
    onNewMask: () -> Unit,
    onRandomMask: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val haptic = LocalHapticFeedback.current
    val createDescription = stringResource(R.string.email_list_create_description)
    val closeDescription = stringResource(R.string.list_fab_close_menu)
    val newLabel = stringResource(R.string.email_list_create_fab)

    // Specs are read here: the transitionSpec lambda below is not composable.
    val motion = MaterialTheme.motionScheme
    val enterFade = motion.defaultEffectsSpec<Float>()
    val enterScale = motion.defaultSpatialSpec<Float>()
    val exitFade = motion.fastEffectsSpec<Float>()
    val exitScale = motion.fastSpatialSpec<Float>()

    FloatingActionButtonMenu(
        expanded = expanded,
        modifier = modifier,
        button = {
            AnimatedContent(
                targetState = extended && !expanded,
                transitionSpec = {
                    (fadeIn(enterFade) + scaleIn(enterScale, initialScale = 0.85f))
                        .togetherWith(fadeOut(exitFade) + scaleOut(exitScale, targetScale = 0.85f))
                },
                label = "fabVariant",
            ) { showExtended ->
                if (showExtended) {
                    ExtendedFloatingActionButton(
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.ToggleOn)
                            onExpandedChange(true)
                        },
                        // Material clears the label's semantics inside the extended FAB, so
                        // without a description the button has no name at all. It must
                        // equal the visible label (WCAG 2.5.3 Label in Name): "Create new
                        // masked address" is not "Nowa maska" in Polish.
                        modifier = Modifier.semantics { contentDescription = newLabel },
                        icon = {
                            if (isCreating) {
                                LoadingIndicator(modifier = Modifier.size(24.dp))
                            } else {
                                Icon(Icons.Filled.Add, contentDescription = null)
                            }
                        },
                        text = { Text(newLabel) },
                    )
                } else {
                    // The toggle starts unchecked and flips a frame later, so the
                    // + -> x morph plays when the menu opens from the extended FAB.
                    var checked by remember { mutableStateOf(false) }
                    LaunchedEffect(expanded) { checked = expanded }
                    ToggleFloatingActionButton(
                        checked = checked,
                        onCheckedChange = { open ->
                            haptic.performHapticFeedback(
                                if (open) HapticFeedbackType.ToggleOn else HapticFeedbackType.ToggleOff,
                            )
                            onExpandedChange(open)
                        },
                        modifier = Modifier.semantics {
                            contentDescription = if (expanded) closeDescription else createDescription
                        },
                    ) {
                        if (isCreating && !expanded) {
                            LoadingIndicator(modifier = Modifier.size(24.dp))
                        } else {
                            val icon by remember {
                                derivedStateOf { if (checkedProgress > 0.5f) Icons.Filled.Close else Icons.Filled.Add }
                            }
                            Icon(
                                imageVector = icon,
                                contentDescription = null,
                                modifier = with(ToggleFloatingActionButtonDefaults) {
                                    Modifier.animateIcon(checkedProgress = { checkedProgress })
                                },
                            )
                        }
                    }
                }
            }
        },
    ) {
        FloatingActionButtonMenuItem(
            onClick = onRandomMask,
            text = { Text(stringResource(R.string.list_random_mask)) },
            icon = { Icon(Icons.Filled.Casino, contentDescription = null) },
        )
        FloatingActionButtonMenuItem(
            onClick = onNewMask,
            text = { Text(newLabel) },
            icon = { Icon(Icons.Filled.Edit, contentDescription = null) },
        )
    }
}

@Composable
private fun OfflineBanner(text: String, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp)
            .clip(MaterialTheme.shapes.large)
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .padding(horizontal = 16.dp, vertical = 12.dp)
            // The one thing on this screen that changes what the data MEANS —
            // these masks may no longer exist. Drawn only, it never reached a
            // screen reader; the row now announces itself when it appears and
            // reads as one phrase instead of an unlabelled icon plus text.
            .semantics(mergeDescendants = true) {
                liveRegion = LiveRegionMode.Polite
                contentDescription = text
            },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Icon(
            imageVector = Icons.Filled.CloudOff,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(20.dp),
        )
        Text(
            text = text,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
internal fun InlineErrorBanner(
    text: String,
    actionLabel: String,
    onAction: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp)
            // Keep the recovery button as a separate semantics node. Merging
            // descendants here used to make the banner readable as one phrase,
            // but would hide the newly actionable control from TalkBack.
            .semantics {
                liveRegion = LiveRegionMode.Polite
            },
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.errorContainer,
        contentColor = MaterialTheme.colorScheme.onErrorContainer,
    ) {
        Row(
            modifier = Modifier.padding(start = 16.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Icon(
                imageVector = Icons.Filled.ErrorOutline,
                contentDescription = null,
                modifier = Modifier.size(20.dp),
            )
            Text(
                text = text,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.weight(1f),
            )
            TextButton(
                onClick = onAction,
                colors = ButtonDefaults.textButtonColors(
                    contentColor = MaterialTheme.colorScheme.onErrorContainer,
                ),
            ) {
                Text(actionLabel, style = MaterialTheme.typography.labelLargeEmphasized)
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Previews (stateless content, fake data, no Hilt)
// ---------------------------------------------------------------------------

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

private fun populated() = MaskedEmailListUiState(emails = previewMasks, filteredEmails = previewMasks)

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
    MaskedEmailListUiState(errorRes = R.string.error_load_emails),
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
    InlineErrorBanner(text = "Could not refresh", actionLabel = "Retry", onAction = {})
}
