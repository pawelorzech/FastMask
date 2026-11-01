package com.fastmask.ui.detail

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Unarchive
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ButtonGroup
import androidx.compose.material3.ButtonGroupDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.ToggleButton
import androidx.compose.material3.ToggleButtonDefaults
import androidx.compose.material3.ToggleButtonShapes
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.ParagraphStyle
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.LineBreak
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import androidx.navigation.NavBackStackEntry
import com.fastmask.R
import com.fastmask.domain.model.EmailState
import com.fastmask.domain.model.MaskedEmail
import com.fastmask.ui.accessibility.politeLiveRegion
import com.fastmask.ui.accessibility.screenHeading
import com.fastmask.ui.common.copyToClipboard
import com.fastmask.ui.components.ButtonLoadingLabel
import com.fastmask.ui.components.ConfirmDialog
import com.fastmask.ui.components.DemoBanner
import com.fastmask.ui.components.expressive.SectionHeader
import com.fastmask.ui.components.expressive.SegmentedGap
import com.fastmask.ui.components.expressive.SegmentedGroup
import com.fastmask.ui.components.expressive.SegmentedRow
import com.fastmask.ui.components.expressive.StateChip
import com.fastmask.ui.components.expressive.StateMorphAvatar
import com.fastmask.ui.components.expressive.segmentedShape
import com.fastmask.ui.navigation.maskContainer
import com.fastmask.ui.theme.FastMaskTheme
import com.fastmask.ui.util.RelativeTime
import kotlinx.coroutines.delay
import java.time.Instant

/** How long the copy button shows its "Copied" confirmation. */
private const val COPIED_FEEDBACK_MS = 1800L

/** Hero scroll offset after which the app bar title fades in. */
private const val TITLE_REVEAL_SCROLL_DP = 120

/**
 * Motion-scheme specs resolved in composition. `transitionSpec` lambdas are not
 * composable, so they capture these instead of reading `MaterialTheme` directly.
 */
private class Motion(
    val fastEffects: FiniteAnimationSpec<Float>,
    val defaultEffects: FiniteAnimationSpec<Float>,
    val fastSpatial: FiniteAnimationSpec<Float>,
    val defaultSpatial: FiniteAnimationSpec<Float>,
    val defaultSpatialSize: FiniteAnimationSpec<IntSize>,
)

@Composable
private fun rememberMotion(): Motion {
    val scheme = MaterialTheme.motionScheme
    return Motion(
        fastEffects = scheme.fastEffectsSpec(),
        defaultEffects = scheme.defaultEffectsSpec(),
        fastSpatial = scheme.fastSpatialSpec(),
        defaultSpatial = scheme.defaultSpatialSpec(),
        defaultSpatialSize = scheme.defaultSpatialSpec(),
    )
}

@Composable
fun MaskedEmailDetailScreen(
    onNavigateBack: () -> Unit,
    onArchived: (String, EmailState) -> Unit,
    onSignInFromBanner: () -> Unit,
    viewModel: MaskedEmailDetailViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    var showArchiveDialog by remember { mutableStateOf(false) }
    var showDiscardDialog by remember { mutableStateOf(false) }
    val snackbarHostState = remember { SnackbarHostState() }

    val isDirty = uiState.hasChanges
    val onBack = { if (isDirty) showDiscardDialog = true else onNavigateBack() }
    BackHandler(enabled = isDirty) { showDiscardDialog = true }

    val updatedMessage = stringResource(R.string.email_detail_updated)

    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                is MaskedEmailDetailEvent.Updated ->
                    snackbarHostState.showSnackbar(updatedMessage, duration = SnackbarDuration.Short)
                is MaskedEmailDetailEvent.Deleted -> {
                    // Navigate straight back and let the list host the "archived —
                    // undo" snackbar, so the undo action lives where the mask
                    // reappears. No blocking snackbar here.
                    onArchived(event.id, event.previousState)
                }
            }
        }
    }

    if (showArchiveDialog) {
        ConfirmDialog(
            icon = Icons.Filled.Archive,
            title = stringResource(R.string.email_detail_delete_dialog_title),
            message = stringResource(R.string.email_detail_delete_dialog_message),
            confirmText = stringResource(R.string.email_detail_delete_confirm),
            dismissText = stringResource(R.string.email_detail_delete_cancel),
            onConfirm = {
                showArchiveDialog = false
                viewModel.delete()
            },
            onDismiss = { showArchiveDialog = false },
        )
    }

    if (showDiscardDialog) {
        ConfirmDialog(
            title = stringResource(R.string.discard_changes_title),
            message = stringResource(R.string.discard_changes_message),
            confirmText = stringResource(R.string.discard_changes_confirm),
            dismissText = stringResource(R.string.discard_changes_cancel),
            onConfirm = { showDiscardDialog = false; onNavigateBack() },
            onDismiss = { showDiscardDialog = false },
        )
    }

    // The nav argument names the list row this screen grows out of; reading it
    // here (instead of from the loaded mask) keeps the shared-bounds key stable
    // from the very first frame, before the mask has loaded.
    val containerId = (LocalViewModelStoreOwner.current as? NavBackStackEntry)
        ?.arguments?.getString("emailId")

    MaskedEmailDetailContent(
        uiState = uiState,
        containerId = containerId,
        snackbarHostState = snackbarHostState,
        banner = { DemoBanner(onSignInClick = onSignInFromBanner) },
        onBack = onBack,
        onRetry = { viewModel.loadEmail() },
        onDescriptionChange = viewModel::onDescriptionChange,
        onForDomainChange = viewModel::onForDomainChange,
        onUrlChange = viewModel::onUrlChange,
        onEnable = viewModel::enable,
        onDisable = viewModel::disable,
        onSaveChanges = viewModel::saveChanges,
        onCopyEmail = { addr -> copyToClipboard(context, addr) },
        onArchiveClick = { showArchiveDialog = true },
    )
}

@Composable
internal fun MaskedEmailDetailContent(
    uiState: MaskedEmailDetailUiState,
    containerId: String?,
    snackbarHostState: SnackbarHostState,
    banner: @Composable () -> Unit,
    onBack: () -> Unit,
    onRetry: () -> Unit,
    onDescriptionChange: (String) -> Unit,
    onForDomainChange: (String) -> Unit,
    onUrlChange: (String) -> Unit,
    onEnable: () -> Unit,
    onDisable: () -> Unit,
    onSaveChanges: () -> Unit,
    onCopyEmail: (String) -> Unit,
    onArchiveClick: () -> Unit,
    startEditing: Boolean = false,
) {
    val scrollState = rememberScrollState()
    val revealPx = with(LocalDensity.current) { TITLE_REVEAL_SCROLL_DP.dp.toPx() }
    val titleVisible = scrollState.value > revealPx
    val raised = scrollState.value > 0

    val titleAlpha by animateFloatAsState(
        targetValue = if (titleVisible) 1f else 0f,
        animationSpec = MaterialTheme.motionScheme.defaultEffectsSpec(),
        label = "titleAlpha",
    )
    val barColor by animateColorAsState(
        targetValue = if (raised) MaterialTheme.colorScheme.surfaceContainer else MaterialTheme.colorScheme.surface,
        animationSpec = MaterialTheme.motionScheme.defaultEffectsSpec(),
        label = "barColor",
    )

    val email = uiState.email
    val backDesc = stringResource(R.string.navigate_back)
    val containerModifier = if (containerId != null) Modifier.maskContainer(containerId) else Modifier

    Scaffold(
        modifier = containerModifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.surface,
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
        topBar = {
            Column {
                // Demo mode banner (auto-hides in REAL mode).
                banner()
                TopAppBar(
                    title = {
                        Text(
                            text = email?.email?.substringBefore('@').orEmpty(),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            // The hero already carries the heading, so this faded
                            // duplicate stays out of the accessibility tree.
                            modifier = Modifier
                                .graphicsLayer { alpha = titleAlpha }
                                .clearAndSetSemantics { },
                        )
                    },
                    navigationIcon = {
                        IconButton(onClick = onBack, shapes = IconButtonDefaults.shapes()) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = backDesc,
                            )
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = barColor),
                )
            }
        },
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
        ) {
            when {
                uiState.isLoading && email == null -> Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) { LoadingIndicator() }

                uiState.errorRes != null && email == null -> DetailErrorState(
                    message = stringResource(uiState.errorRes),
                    onRetry = onRetry,
                )

                email != null -> DetailBody(
                    uiState = uiState,
                    email = email,
                    scrollState = scrollState,
                    onDescriptionChange = onDescriptionChange,
                    onForDomainChange = onForDomainChange,
                    onUrlChange = onUrlChange,
                    onEnable = onEnable,
                    onDisable = onDisable,
                    onSaveChanges = onSaveChanges,
                    onCopyEmail = onCopyEmail,
                    onArchiveClick = onArchiveClick,
                    startEditing = startEditing,
                )
            }
        }
    }
}

@Composable
private fun DetailBody(
    uiState: MaskedEmailDetailUiState,
    email: MaskedEmail,
    scrollState: ScrollState,
    onDescriptionChange: (String) -> Unit,
    onForDomainChange: (String) -> Unit,
    onUrlChange: (String) -> Unit,
    onEnable: () -> Unit,
    onDisable: () -> Unit,
    onSaveChanges: () -> Unit,
    onCopyEmail: (String) -> Unit,
    onArchiveClick: () -> Unit,
    startEditing: Boolean,
) {
    val haptic = LocalHapticFeedback.current
    val motion = rememberMotion()
    var editing by rememberSaveable { mutableStateOf(startEditing) }

    // Leave edit mode once a save lands: the in-flight flag drops and the draft
    // matches the server again. A failed save keeps the draft (hasChanges) and
    // therefore stays in edit mode with the inline error.
    val saving = uiState.updateOperation == MaskedEmailDetailUpdate.SAVE_CHANGES
    var wasSaving by remember { mutableStateOf(false) }
    LaunchedEffect(saving) {
        if (wasSaving && !saving && !uiState.hasChanges) editing = false
        wasSaving = saving
    }

    // Copy confirmation: icon + label flip to check / "Copied" and back.
    var copyNonce by remember { mutableIntStateOf(0) }
    var copied by remember { mutableStateOf(false) }
    LaunchedEffect(copyNonce) {
        if (copyNonce > 0) {
            copied = true
            delay(COPIED_FEEDBACK_MS)
            copied = false
        }
    }

    val revertDraft = {
        onDescriptionChange(email.description.orEmpty())
        onForDomainChange(email.forDomain.orEmpty())
        onUrlChange(email.url.orEmpty())
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(bottom = 32.dp),
    ) {
        Hero(email = email)

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(top = 20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            CopyButton(
                copied = copied,
                onClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.Confirm)
                    onCopyEmail(email.email)
                    copyNonce++
                },
            )
            StatusControl(
                state = email.state,
                toggling = uiState.updateOperation == MaskedEmailDetailUpdate.TOGGLE_STATE,
                busy = uiState.isUpdating || uiState.isDeleting,
                onEnable = {
                    haptic.performHapticFeedback(HapticFeedbackType.ToggleOn)
                    onEnable()
                },
                onDisable = {
                    haptic.performHapticFeedback(HapticFeedbackType.ToggleOff)
                    onDisable()
                },
            )
        }

        // Details header with the edit toggle.
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(end = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            SectionHeader(
                text = stringResource(R.string.email_detail_edit_title),
                modifier = Modifier.weight(1f),
            )
            IconButton(
                onClick = {
                    // Closing the editor discards the draft, like Cancel.
                    if (editing) revertDraft()
                    editing = !editing
                },
                enabled = !uiState.isUpdating,
                shapes = IconButtonDefaults.shapes(),
            ) {
                AnimatedContent(
                    targetState = editing,
                    transitionSpec = {
                        (fadeIn(motion.fastEffects) +
                            scaleIn(motion.fastSpatial, initialScale = 0.6f)) togetherWith
                            (fadeOut(motion.fastEffects) +
                                scaleOut(motion.fastSpatial, targetScale = 0.6f))
                    },
                    label = "editIcon",
                ) { isEditing ->
                    Icon(
                        imageVector = if (isEditing) Icons.Filled.Close else Icons.Filled.Edit,
                        contentDescription = stringResource(
                            if (isEditing) R.string.email_detail_edit_stop else R.string.email_detail_edit_action,
                        ),
                    )
                }
            }
        }

        Box(modifier = Modifier.padding(horizontal = 16.dp)) {
            AnimatedContent(
                targetState = editing,
                transitionSpec = {
                    (fadeIn(motion.defaultEffects) togetherWith
                        fadeOut(motion.fastEffects)) using
                        SizeTransform(clip = false) { _, _ -> motion.defaultSpatialSize }
                },
                label = "detailsMode",
            ) { isEditing ->
                if (isEditing) {
                    EditFields(
                        uiState = uiState,
                        onDescriptionChange = onDescriptionChange,
                        onForDomainChange = onForDomainChange,
                        onUrlChange = onUrlChange,
                    )
                } else {
                    ReadFields(email = email)
                }
            }
        }

        if (uiState.errorRes != null) {
            Text(
                text = stringResource(uiState.errorRes),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier
                    .politeLiveRegion()
                    .padding(horizontal = 32.dp)
                    .padding(top = 12.dp),
            )
        }

        AnimatedVisibility(
            visible = editing,
            enter = fadeIn(MaterialTheme.motionScheme.defaultEffectsSpec()) +
                expandVertically(MaterialTheme.motionScheme.defaultSpatialSpec()),
            exit = fadeOut(MaterialTheme.motionScheme.fastEffectsSpec()) +
                shrinkVertically(MaterialTheme.motionScheme.fastSpatialSpec()),
        ) {
            EditActions(
                saving = saving,
                busy = uiState.isUpdating,
                canSave = uiState.hasChanges,
                onCancel = {
                    revertDraft()
                    editing = false
                },
                onSave = onSaveChanges,
                modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 14.dp),
            )
        }

        AnimatedVisibility(
            visible = email.state != EmailState.DELETED,
            enter = fadeIn(MaterialTheme.motionScheme.defaultEffectsSpec()) +
                expandVertically(MaterialTheme.motionScheme.defaultSpatialSpec()),
            exit = fadeOut(MaterialTheme.motionScheme.fastEffectsSpec()) +
                shrinkVertically(MaterialTheme.motionScheme.fastSpatialSpec()),
        ) {
            ArchiveButton(
                deleting = uiState.isDeleting,
                enabled = !uiState.isUpdating && !uiState.isDeleting,
                onClick = onArchiveClick,
                modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 28.dp),
            )
        }
    }
}

// --- hero -----------------------------------------------------------------

@Composable
private fun Hero(email: MaskedEmail) {
    val local = email.email.substringBefore('@')
    val domainPart = email.email.substringAfter('@', missingDelimiterValue = "")
    val seed = email.forDomain?.takeIf { it.isNotBlank() } ?: email.email

    val onSurface = MaterialTheme.colorScheme.onSurface
    val onSurfaceVariant = MaterialTheme.colorScheme.onSurfaceVariant
    val domainStyle = MaterialTheme.typography.titleMedium

    // One Text whose string is exactly the address (selection, copy and the
    // semantics tests all read it), laid out as two paragraphs: the local part
    // in the headline style, "@domain" lighter underneath. A paragraph style
    // starts a new line without adding a character.
    val address = remember(email.email, onSurface, onSurfaceVariant, domainStyle) {
        buildAnnotatedString {
            withStyle(ParagraphStyle(lineBreak = LineBreak.Simple)) {
                withStyle(SpanStyle(color = onSurface)) { append(local) }
            }
            if (domainPart.isNotEmpty()) {
                withStyle(ParagraphStyle(lineHeight = 28.sp, lineBreak = LineBreak.Simple)) {
                    withStyle(
                        SpanStyle(
                            color = onSurfaceVariant,
                            fontSize = domainStyle.fontSize,
                            fontWeight = domainStyle.fontWeight,
                        ),
                    ) { append("@$domainPart") }
                }
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp)
            .padding(top = 8.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        StateMorphAvatar(
            initial = seed,
            state = email.state,
            size = 88.dp,
        )
        SelectionContainer {
            Text(
                text = address,
                style = MaterialTheme.typography.headlineMediumEmphasized,
                modifier = Modifier.screenHeading(),
            )
        }
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            StateChip(state = email.state)
            Text(
                text = email.forDomain?.takeIf { it.isNotBlank() }
                    ?: stringResource(R.string.email_detail_no_domain),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f, fill = false),
            )
        }
    }
}

// --- actions --------------------------------------------------------------

@Composable
private fun CopyButton(copied: Boolean, onClick: () -> Unit) {
    val motion = rememberMotion()
    val height = ButtonDefaults.MediumContainerHeight
    Button(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = height),
        shapes = ButtonDefaults.shapes(),
        contentPadding = ButtonDefaults.contentPaddingFor(height),
    ) {
        AnimatedContent(
            targetState = copied,
            transitionSpec = {
                (fadeIn(motion.fastEffects) +
                    scaleIn(motion.fastSpatial, initialScale = 0.85f)) togetherWith
                    fadeOut(motion.fastEffects)
            },
            label = "copyLabel",
        ) { isCopied ->
            Row(
                horizontalArrangement = Arrangement.spacedBy(ButtonDefaults.iconSpacingFor(height)),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = if (isCopied) Icons.Filled.Check else Icons.Filled.ContentCopy,
                    contentDescription = null,
                    modifier = Modifier.size(ButtonDefaults.iconSizeFor(height)),
                )
                Text(
                    text = stringResource(
                        if (isCopied) R.string.email_detail_copied else R.string.email_detail_copy_email,
                    ),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.politeLiveRegion(),
                )
            }
        }
    }
}

@Composable
private fun StatusControl(
    state: EmailState,
    toggling: Boolean,
    busy: Boolean,
    onEnable: () -> Unit,
    onDisable: () -> Unit,
) {
    val motion = rememberMotion()
    // Optimistic selection: show the tapped option immediately while the request
    // is in flight. When the request fails the in-flight flag drops without the
    // state having changed, which rolls the selection back.
    var pending by remember { mutableStateOf<EmailState?>(null) }
    val shown = if (toggling) pending ?: state else state
    val statusDesc = stringResource(R.string.email_detail_status)

    AnimatedContent(
        targetState = state == EmailState.DELETED,
        transitionSpec = {
            (fadeIn(motion.defaultEffects) +
                scaleIn(motion.defaultSpatial, initialScale = 0.92f)) togetherWith
                fadeOut(motion.fastEffects) using
                SizeTransform(clip = false) { _, _ -> motion.defaultSpatialSize }
        },
        label = "statusControl",
    ) { archived ->
        val height = ButtonDefaults.MediumContainerHeight
        if (archived) {
            FilledTonalButton(
                onClick = {
                    pending = EmailState.ENABLED
                    onEnable()
                },
                enabled = !busy,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = height),
                shapes = ButtonDefaults.shapes(),
                contentPadding = ButtonDefaults.contentPaddingFor(height),
            ) {
                ButtonLoadingLabel(
                    loading = toggling,
                    loadingDescription = stringResource(R.string.state_working),
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(ButtonDefaults.iconSpacingFor(height)),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Unarchive,
                            contentDescription = null,
                            modifier = Modifier.size(ButtonDefaults.iconSizeFor(height)),
                        )
                        Text(
                            text = stringResource(R.string.email_detail_enable),
                            style = MaterialTheme.typography.titleMedium,
                        )
                    }
                }
            }
        } else {
            val activeSelected = shown == EmailState.ENABLED || shown == EmailState.PENDING
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .selectableGroup()
                    .semantics { contentDescription = statusDesc },
                horizontalArrangement = Arrangement.spacedBy(ButtonGroupDefaults.ConnectedSpaceBetween),
            ) {
                StatusToggle(
                    label = stringResource(R.string.state_enabled),
                    checked = activeSelected,
                    loading = toggling && activeSelected,
                    shapes = ButtonGroupDefaults.connectedLeadingButtonShapes(),
                    modifier = Modifier.weight(1f),
                    onCheckedChange = { checked ->
                        if (checked && !busy) {
                            pending = EmailState.ENABLED
                            onEnable()
                        }
                    },
                )
                StatusToggle(
                    label = stringResource(R.string.state_disabled),
                    checked = !activeSelected,
                    loading = toggling && !activeSelected,
                    shapes = ButtonGroupDefaults.connectedTrailingButtonShapes(),
                    modifier = Modifier.weight(1f),
                    onCheckedChange = { checked ->
                        if (checked && !busy) {
                            pending = EmailState.DISABLED
                            onDisable()
                        }
                    },
                )
            }
        }
    }
}

@Composable
private fun StatusToggle(
    label: String,
    checked: Boolean,
    loading: Boolean,
    shapes: ToggleButtonShapes,
    modifier: Modifier,
    onCheckedChange: (Boolean) -> Unit,
) {
    val height = ButtonDefaults.MediumContainerHeight
    ToggleButton(
        checked = checked,
        onCheckedChange = onCheckedChange,
        // Exactly one option is always chosen: a radio choice for screen readers.
        modifier = modifier
            .heightIn(min = height)
            .semantics { role = Role.RadioButton },
        shapes = shapes,
        contentPadding = ToggleButtonDefaults.contentPaddingFor(height),
    ) {
        AnimatedVisibility(
            visible = checked,
            enter = fadeIn(MaterialTheme.motionScheme.fastEffectsSpec()) +
                expandHorizontally(MaterialTheme.motionScheme.fastSpatialSpec()),
            exit = fadeOut(MaterialTheme.motionScheme.fastEffectsSpec()) +
                shrinkHorizontally(MaterialTheme.motionScheme.fastSpatialSpec()),
        ) {
            Box(modifier = Modifier.padding(end = ToggleButtonDefaults.IconSpacing)) {
                if (loading) {
                    LoadingIndicator(modifier = Modifier.size(ToggleButtonDefaults.IconSize))
                } else {
                    Icon(
                        imageVector = Icons.Filled.Check,
                        contentDescription = null,
                        modifier = Modifier.size(ToggleButtonDefaults.IconSize),
                    )
                }
            }
        }
        Text(text = label, style = MaterialTheme.typography.titleMedium)
    }
}

@Composable
private fun ArchiveButton(
    deleting: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val height = ButtonDefaults.MediumContainerHeight
    val error = MaterialTheme.colorScheme.error
    val archiveDesc = stringResource(R.string.email_detail_delete)
    OutlinedButton(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = height)
            // The label is also the accessible name; spelled out so the button
            // stays addressable by name (TalkBack actions, instrumented tests).
            .semantics { contentDescription = archiveDesc },
        shapes = ButtonDefaults.shapes(),
        colors = ButtonDefaults.outlinedButtonColors(contentColor = error),
        border = BorderStroke(1.dp, error.copy(alpha = if (enabled) 1f else 0.38f)),
        contentPadding = ButtonDefaults.contentPaddingFor(height),
    ) {
        ButtonLoadingLabel(
            loading = deleting,
            loadingDescription = stringResource(R.string.state_working),
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(ButtonDefaults.iconSpacingFor(height)),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = Icons.Filled.Archive,
                    contentDescription = null,
                    modifier = Modifier.size(ButtonDefaults.iconSizeFor(height)),
                )
                Text(
                    text = stringResource(R.string.email_detail_delete),
                    style = MaterialTheme.typography.titleMedium,
                )
            }
        }
    }
}

@Composable
private fun EditActions(
    saving: Boolean,
    busy: Boolean,
    canSave: Boolean,
    onCancel: () -> Unit,
    onSave: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val height = ButtonDefaults.MediumContainerHeight
    val cancelText = stringResource(R.string.email_detail_delete_cancel)
    val saveText = stringResource(R.string.email_detail_save)
    val workingDesc = stringResource(R.string.state_working)
    ButtonGroup(
        overflowIndicator = { menuState -> ButtonGroupDefaults.OverflowIndicator(menuState) },
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
    ) {
        customItem(
            buttonGroupContent = {
                val source = remember { MutableInteractionSource() }
                OutlinedButton(
                    onClick = onCancel,
                    enabled = !busy,
                    interactionSource = source,
                    modifier = Modifier
                        .heightIn(min = height)
                        .animateWidth(source),
                    shapes = ButtonDefaults.shapes(),
                    contentPadding = ButtonDefaults.contentPaddingFor(height),
                ) { Text(text = cancelText, style = MaterialTheme.typography.titleMedium) }
            },
            menuContent = { state ->
                DropdownMenuItem(
                    text = { Text(cancelText) },
                    onClick = { onCancel(); state.dismiss() },
                )
            },
        )
        customItem(
            buttonGroupContent = {
                val source = remember { MutableInteractionSource() }
                Button(
                    onClick = onSave,
                    enabled = canSave && !busy,
                    interactionSource = source,
                    modifier = Modifier
                        .heightIn(min = height)
                        .animateWidth(source),
                    shapes = ButtonDefaults.shapes(),
                    contentPadding = ButtonDefaults.contentPaddingFor(height),
                ) {
                    ButtonLoadingLabel(loading = saving, loadingDescription = workingDesc) {
                        Text(text = saveText, style = MaterialTheme.typography.titleMedium)
                    }
                }
            },
            menuContent = { state ->
                DropdownMenuItem(
                    text = { Text(saveText) },
                    enabled = canSave && !busy,
                    onClick = { onSave(); state.dismiss() },
                )
            },
        )
    }
}

// --- details --------------------------------------------------------------

@Composable
private fun ReadFields(email: MaskedEmail) {
    val dash = "—"
    val rows = listOf(
        stringResource(R.string.email_detail_description_label) to
            (email.description?.takeIf { it.isNotBlank() } ?: dash),
        stringResource(R.string.email_detail_domain_label) to
            (email.forDomain?.takeIf { it.isNotBlank() } ?: dash),
        stringResource(R.string.email_detail_url_label) to
            (email.url?.takeIf { it.isNotBlank() } ?: dash),
        stringResource(R.string.email_detail_created) to
            (email.createdAt?.let { RelativeTime.full(it) } ?: dash),
        stringResource(R.string.email_detail_last_message) to
            (email.lastMessageAt?.let { RelativeTime.full(it) } ?: stringResource(R.string.time_never)),
        stringResource(R.string.email_detail_created_by) to (email.createdBy ?: dash),
    )
    SegmentedGroup(count = rows.size) { index ->
        val (label, value) = rows[index]
        SegmentedRow(
            index = index,
            count = rows.size,
            // Small label on top (headline slot), the wrapping, selectable value
            // beneath it (supporting slot). Neither is truncated: long values
            // wrap instead of ellipsizing.
            supporting = {
                SelectionContainer {
                    Text(
                        text = value,
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }
            },
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun EditFields(
    uiState: MaskedEmailDetailUiState,
    onDescriptionChange: (String) -> Unit,
    onForDomainChange: (String) -> Unit,
    onUrlChange: (String) -> Unit,
) {
    val domainFocus = remember { FocusRequester() }
    val urlFocus = remember { FocusRequester() }
    val enabled = !uiState.isUpdating
    Column(verticalArrangement = Arrangement.spacedBy(SegmentedGap)) {
        EditFieldRow(
            index = 0,
            value = uiState.editedDescription,
            onValueChange = onDescriptionChange,
            label = stringResource(R.string.email_detail_description_label),
            placeholder = stringResource(R.string.create_email_description_placeholder),
            enabled = enabled,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
            keyboardActions = KeyboardActions(onNext = { domainFocus.requestFocus() }),
        )
        EditFieldRow(
            index = 1,
            value = uiState.editedForDomain,
            onValueChange = onForDomainChange,
            label = stringResource(R.string.email_detail_domain_label),
            placeholder = stringResource(R.string.create_email_domain_placeholder),
            enabled = enabled,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Next),
            keyboardActions = KeyboardActions(onNext = { urlFocus.requestFocus() }),
            modifier = Modifier.focusRequester(domainFocus),
        )
        EditFieldRow(
            index = 2,
            value = uiState.editedUrl,
            onValueChange = onUrlChange,
            label = stringResource(R.string.email_detail_url_label),
            placeholder = stringResource(R.string.create_email_url_placeholder),
            enabled = enabled,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Done),
            modifier = Modifier.focusRequester(urlFocus),
        )
    }
}

@Composable
private fun EditFieldRow(
    index: Int,
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    placeholder: String,
    enabled: Boolean,
    keyboardOptions: KeyboardOptions,
    modifier: Modifier = Modifier,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
) {
    val container = MaterialTheme.colorScheme.surfaceContainer
    TextField(
        value = value,
        onValueChange = onValueChange,
        enabled = enabled,
        singleLine = true,
        label = { Text(label) },
        placeholder = { Text(placeholder) },
        keyboardOptions = keyboardOptions,
        keyboardActions = keyboardActions,
        shape = segmentedShape(index = index, count = 3),
        colors = TextFieldDefaults.colors(
            focusedContainerColor = container,
            unfocusedContainerColor = container,
            disabledContainerColor = container,
            unfocusedIndicatorColor = Color.Transparent,
            disabledIndicatorColor = Color.Transparent,
        ),
        // The visible label is part of the field; naming the node explicitly
        // keeps it addressable by label (TalkBack, instrumented tests).
        modifier = modifier
            .fillMaxWidth()
            .semantics { contentDescription = label },
    )
}

// --- error ----------------------------------------------------------------

@Composable
private fun DetailErrorState(message: String, onRetry: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box(
            modifier = Modifier
                .size(112.dp)
                .clip(MaterialShapes.SoftBurst.toShape())
                .background(MaterialTheme.colorScheme.errorContainer),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Filled.ErrorOutline,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onErrorContainer,
                modifier = Modifier.size(40.dp),
            )
        }
        Spacer(Modifier.size(20.dp))
        Text(
            text = stringResource(R.string.email_detail_error_load),
            style = MaterialTheme.typography.titleLargeEmphasized,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
            modifier = Modifier.politeLiveRegion(),
        )
        Spacer(Modifier.size(8.dp))
        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.size(24.dp))
        // loadEmail() is fully re-runnable, so the error state offers a retry
        // instead of a dead end.
        FilledTonalButton(onClick = onRetry, shapes = ButtonDefaults.shapes()) {
            Text(stringResource(R.string.error_retry))
        }
    }
}

// --- previews -------------------------------------------------------------

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
private fun PreviewHost(uiState: MaskedEmailDetailUiState, startEditing: Boolean = false) {
    FastMaskTheme {
        MaskedEmailDetailContent(
            uiState = uiState,
            containerId = null,
            snackbarHostState = remember { SnackbarHostState() },
            banner = {},
            onBack = {},
            onRetry = {},
            onDescriptionChange = {},
            onForDomainChange = {},
            onUrlChange = {},
            onEnable = {},
            onDisable = {},
            onSaveChanges = {},
            onCopyEmail = {},
            onArchiveClick = {},
            startEditing = startEditing,
        )
    }
}

@Preview(name = "Editing", showBackground = true, heightDp = 1000)
@Composable
private fun DetailEditingPreview() = PreviewHost(
    previewState().copy(editedDescription = "Figma work account"),
    startEditing = true,
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
    PreviewHost(MaskedEmailDetailUiState(errorRes = R.string.email_detail_error_load))

@Preview(name = "Long address", showBackground = true, heightDp = 900)
@Composable
private fun DetailLongAddressPreview() = PreviewHost(
    previewState(previewMask.copy(email = "a-very-long-prefix-name-for-testing-wrapping.bright4821@fastmail.com")),
)
