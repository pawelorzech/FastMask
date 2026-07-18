package com.fastmask.ui.list

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import com.fastmask.R
import com.fastmask.domain.model.AppMode
import com.fastmask.domain.model.EmailState
import com.fastmask.domain.model.MaskedEmail
import com.fastmask.ui.components.DemoBanner
import com.fastmask.ui.components.DesignCard
import com.fastmask.ui.components.MonoEyebrow
import com.fastmask.ui.components.PillButton
import com.fastmask.ui.components.PillButtonVariant
import com.fastmask.ui.components.PillIconButton
import com.fastmask.ui.components.StateDot
import com.fastmask.ui.components.TooltipPosition
import com.fastmask.ui.components.TutorialOverlay
import com.fastmask.ui.components.TutorialStep
import com.fastmask.ui.theme.FastMaskExtras
import com.fastmask.ui.theme.JetBrainsMono
import com.fastmask.ui.theme.MonoSmallStyle
import com.fastmask.ui.theme.MonoTimestampStyle
import com.fastmask.ui.util.RelativeTime
import java.time.Instant
import kotlinx.coroutines.delay

@OptIn(ExperimentalMaterial3Api::class, androidx.compose.animation.ExperimentalSharedTransitionApi::class)
@Composable
fun MaskedEmailListScreen(
    onNavigateToCreate: () -> Unit,
    onNavigateToDetail: (String) -> Unit,
    onNavigateToSettings: () -> Unit,
    onSignInFromBanner: () -> Unit,
    sharedTransitionScope: androidx.compose.animation.SharedTransitionScope,
    animatedContentScope: androidx.compose.animation.AnimatedContentScope,
    viewModel: MaskedEmailListViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()
    val listState = rememberLazyListState()
    val haptic = LocalHapticFeedback.current

    val appMode by viewModel.appMode.collectAsState()
    val tutorialCompleted by viewModel.tutorialCompleted.collectAsState()

    val lifecycleOwner = LocalLifecycleOwner.current
    LaunchedEffect(lifecycleOwner) {
        lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            delay(250L)
            viewModel.refreshMaskedEmails()
        }
    }

    val activeCount = uiState.emails.count { it.state == EmailState.ENABLED || it.state == EmailState.PENDING }
    val totalCount = uiState.emails.size

    // Tutorial coach-mark bounds. Each key corresponds to a step index defined
    // below; values are filled in via Modifier.onGloballyPositioned on the
    // respective UI elements as they enter composition.
    val tutorialBounds = remember { mutableStateMapOf<Int, Rect>() }
    var showTutorial by remember { mutableStateOf(false) }

    // Show the tutorial once all 5 target elements have measured their bounds
    // (only relevant in demo mode and only once per demo session).
    LaunchedEffect(appMode, tutorialCompleted, tutorialBounds.size) {
        showTutorial = appMode == AppMode.DEMO &&
            !tutorialCompleted &&
            tutorialBounds.size >= 5
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        floatingActionButton = {
            CreateFab(
                label = stringResource(R.string.email_list_create_fab),
                description = stringResource(R.string.email_list_create_description),
                onClick = onNavigateToCreate,
                modifier = Modifier.onGloballyPositioned { coords ->
                    tutorialBounds[4] = coords.boundsInRoot()
                },
            )
        },
    ) { paddingValues ->
        Box(modifier = Modifier.fillMaxSize().padding(paddingValues)) {
            Column(modifier = Modifier.fillMaxSize()) {
                // Demo mode banner (auto-hides in REAL mode).
                DemoBanner(onSignInClick = onSignInFromBanner)

                // Header
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 24.dp, end = 20.dp, top = 12.dp, bottom = 8.dp),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.Top,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            MonoEyebrow(
                                text = stringResource(R.string.list_stats, activeCount, totalCount),
                            )
                            Spacer(Modifier.height(6.dp))
                            Text(
                                text = stringResource(R.string.email_list_title),
                                style = MaterialTheme.typography.displayMedium,
                                color = MaterialTheme.colorScheme.onBackground,
                            )
                        }
                        PillIconButton(
                            onClick = onNavigateToSettings,
                            contentDescription = stringResource(R.string.email_list_settings),
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Settings,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp),
                            )
                        }
                    }

                    Spacer(Modifier.height(18.dp))

                    SearchField(
                        query = uiState.searchQuery,
                        onQueryChange = viewModel::onSearchQueryChange,
                        modifier = Modifier.onGloballyPositioned { coords ->
                            tutorialBounds[1] = coords.boundsInRoot()
                        },
                    )

                    Spacer(Modifier.height(12.dp))

                    FilterRow(
                        selected = uiState.selectedFilter,
                        counts = mapOf(
                            EmailFilter.ALL to totalCount,
                            EmailFilter.ENABLED to activeCount,
                            EmailFilter.DISABLED to uiState.emails.count { it.state == EmailState.DISABLED },
                            EmailFilter.DELETED to uiState.emails.count { it.state == EmailState.DELETED },
                        ),
                        onSelect = {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            viewModel.onFilterChange(it)
                        },
                        modifier = Modifier.onGloballyPositioned { coords ->
                            tutorialBounds[2] = coords.boundsInRoot()
                        },
                    )
                }

                Spacer(Modifier.height(8.dp))

                when {
                    uiState.isLoading && uiState.emails.isEmpty() -> {
                        LoadingShimmer()
                    }
                    uiState.errorRes != null && uiState.emails.isEmpty() -> {
                        ErrorBlock(
                            message = stringResource(uiState.errorRes ?: R.string.error_load_emails),
                            onRetry = viewModel::loadMaskedEmails,
                        )
                    }
                    uiState.filteredEmails.isEmpty() -> {
                        EmptyBlock()
                    }
                    else -> {
                        val nowSec = remember(uiState.emails) { Instant.now().epochSecond }
                        PullToRefreshBox(
                            isRefreshing = uiState.isLoading,
                            onRefresh = viewModel::loadMaskedEmails,
                            modifier = Modifier.fillMaxSize(),
                        ) {
                            LazyColumn(
                                state = listState,
                                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 0.dp, bottom = 100.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier.onGloballyPositioned { coords ->
                                    // Step 0 highlights the list as a whole.
                                    tutorialBounds[0] = coords.boundsInRoot()
                                },
                            ) {
                                itemsIndexed(
                                    items = uiState.filteredEmails,
                                    key = { _, e -> e.id },
                                ) { index, email ->
                                    MaskRow(
                                        email = email,
                                        nowSec = nowSec,
                                        onClick = { onNavigateToDetail(email.id) },
                                        modifier = if (index == 0) {
                                            Modifier.onGloballyPositioned { coords ->
                                                // Step 3 highlights the first mask card.
                                                tutorialBounds[3] = coords.boundsInRoot()
                                            }
                                        } else Modifier,
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Tutorial coach marks — overlays the entire scaffold body.
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

@Composable
private fun SearchField(
    query: String,
    onQueryChange: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val extras = FastMaskExtras.current
    val haptic = LocalHapticFeedback.current
    val shape = RoundedCornerShape(14.dp)
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .background(MaterialTheme.colorScheme.surface, shape)
            .border(1.dp, MaterialTheme.colorScheme.outline, shape)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = Icons.Filled.Search,
            contentDescription = null,
            tint = extras.inkMuted,
            modifier = Modifier.size(16.dp),
        )
        Spacer(Modifier.width(10.dp))
        Box(modifier = Modifier.weight(1f)) {
            BasicTextField(
                value = query,
                onValueChange = onQueryChange,
                singleLine = true,
                textStyle = MaterialTheme.typography.bodyMedium.copy(color = MaterialTheme.colorScheme.onSurface),
                cursorBrush = SolidColor(extras.accent),
                modifier = Modifier.fillMaxWidth(),
            )
            if (query.isEmpty()) {
                Text(
                    text = stringResource(R.string.email_list_search_placeholder),
                    style = MaterialTheme.typography.bodyMedium,
                    color = extras.inkMuted,
                )
            }
        }
        if (query.isNotEmpty()) {
            // 40dp touch target (visual glyph stays 16dp) — a11y minimum for a
            // secondary inline control inside the 44dp-tall search field.
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(20.dp))
                    .clickable(role = Role.Button) {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        onQueryChange("")
                    },
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Filled.Close,
                    contentDescription = stringResource(R.string.email_list_clear_search),
                    tint = extras.inkMuted,
                    modifier = Modifier.size(16.dp),
                )
            }
        }
    }
}

@Composable
private fun FilterRow(
    selected: EmailFilter,
    counts: Map<EmailFilter, Int>,
    onSelect: (EmailFilter) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        EmailFilter.entries.forEach { f ->
            FilterPill(
                filter = f,
                count = counts[f] ?: 0,
                selected = selected == f,
                onClick = { onSelect(f) },
            )
        }
    }
}

@Composable
private fun FilterPill(
    filter: EmailFilter,
    count: Int,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val extras = FastMaskExtras.current
    val shape = RoundedCornerShape(100.dp)
    val bg = if (selected) extras.accent else Color.Transparent
    val fg = if (selected) extras.onAccent else extras.inkSoft
    val border = if (selected) extras.accent else MaterialTheme.colorScheme.outline
    val label = stringResource(
        when (filter) {
            EmailFilter.ALL -> R.string.filter_all
            EmailFilter.ENABLED -> R.string.filter_enabled
            EmailFilter.DISABLED -> R.string.filter_disabled
            EmailFilter.DELETED -> R.string.filter_deleted
        },
    )
    Row(
        modifier = Modifier
            .clip(shape)
            .background(bg, shape)
            .border(1.dp, border, shape)
            .clickable(onClick = onClick)
            // Expose the filter's on/off state to TalkBack — visually it is
            // conveyed by color only.
            .semantics { this.selected = selected; role = Role.Tab }
            .padding(horizontal = 12.dp, vertical = 7.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge.copy(fontSize = 12.5.sp),
            color = fg,
        )
        Text(
            text = count.toString(),
            style = MonoSmallStyle,
            color = if (selected) extras.onAccent.copy(alpha = 0.7f) else extras.inkMuted,
        )
    }
}

@Composable
private fun MaskRow(
    email: MaskedEmail,
    nowSec: Long,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val extras = FastMaskExtras.current
    val context = LocalContext.current
    DesignCard(
        modifier = modifier.fillMaxWidth(),
        onClick = onClick,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            StateDot(state = email.state)
            Spacer(Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.Top,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(
                        text = email.displayName,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier
                            .weight(1f)
                            .padding(end = 8.dp),
                    )
                    Text(
                        text = RelativeTime.format(context, email.lastMessageAt ?: email.createdAt, nowSec),
                        style = MonoTimestampStyle,
                        color = extras.inkMuted,
                    )
                }
                Spacer(Modifier.height(2.dp))
                Text(
                    text = email.email,
                    style = MaterialTheme.typography.bodySmall.copy(fontFamily = JetBrainsMono),
                    color = extras.inkMuted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun EmptyBlock() {
    val extras = FastMaskExtras.current
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 32.dp, vertical = 80.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Top,
    ) {
        Text(
            text = stringResource(R.string.email_list_empty),
            style = MaterialTheme.typography.headlineMedium,
            color = extras.inkSoft,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = stringResource(R.string.email_list_empty_sub),
            style = MaterialTheme.typography.bodySmall,
            color = extras.inkMuted,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun LoadingShimmer() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        repeat(6) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(70.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(MaterialTheme.colorScheme.surface)
                    .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(16.dp)),
            )
        }
    }
}

@Composable
private fun ErrorBlock(message: String, onRetry: () -> Unit) {
    val extras = FastMaskExtras.current
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = stringResource(R.string.error_load_emails),
            style = MaterialTheme.typography.headlineSmall,
            color = extras.inkSoft,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = message,
            style = MaterialTheme.typography.bodySmall,
            color = extras.inkMuted,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(20.dp))
        PillButton(
            text = stringResource(R.string.error_retry),
            onClick = onRetry,
            variant = PillButtonVariant.Secondary,
        )
    }
}

@Composable
private fun CreateFab(
    label: String,
    description: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val extras = FastMaskExtras.current
    val haptic = LocalHapticFeedback.current
    val shape = RoundedCornerShape(100.dp)
    Row(
        modifier = modifier
            .clip(shape)
            .background(extras.accent, shape)
            .clickable {
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                onClick()
            }
            .padding(horizontal = 20.dp, vertical = 14.dp)
            .semantics { contentDescription = description },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Icon(
            imageVector = Icons.Filled.Add,
            contentDescription = null,
            tint = extras.onAccent,
            modifier = Modifier.size(18.dp),
        )
        Text(
            text = label,
            color = extras.onAccent,
            style = MaterialTheme.typography.labelLarge,
        )
    }
}
