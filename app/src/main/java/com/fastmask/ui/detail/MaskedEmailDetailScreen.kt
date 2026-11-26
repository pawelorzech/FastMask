package com.fastmask.ui.detail

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.toShape
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.fastmask.R
import com.fastmask.domain.model.EmailState
import com.fastmask.domain.model.MaskedEmail
import com.fastmask.ui.common.ContentWidth
import com.fastmask.core.platform.copyToClipboard
import com.fastmask.ui.common.readableWidth
import com.fastmask.ui.components.ConfirmDialog
import com.fastmask.ui.components.DemoBanner
import com.fastmask.ui.components.ErrorState
import com.fastmask.ui.components.InlineErrorBanner
import com.fastmask.ui.components.ScrollRaisedTopBar
import com.fastmask.ui.components.expressive.SectionHeader
import com.fastmask.ui.components.rememberDiscardChangesGuard
import kotlinx.coroutines.delay

private const val COPIED_FEEDBACK_MS = 1800L

private const val TITLE_REVEAL_SCROLL_DP = 120

@Composable
fun MaskedEmailDetailScreen(
    onNavigateBack: () -> Unit,
    onArchived: (String, EmailState) -> Unit,
    onSignInFromBanner: () -> Unit,
    onReauthenticate: () -> Unit,
    showBackButton: Boolean = true,
    showDemoBanner: Boolean = true,
    viewModel: MaskedEmailDetailViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    var showArchiveDialog by rememberSaveable { mutableStateOf(false) }
    val snackbarHostState = remember { SnackbarHostState() }

    val isDirty = uiState.hasChanges
    val onBack = rememberDiscardChangesGuard(isDirty, onLeave = onNavigateBack)

    val updatedMessage = stringResource(R.string.email_detail_updated)
    val currentOnArchived by rememberUpdatedState(onArchived)

    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                is MaskedEmailDetailEvent.Updated ->
                    snackbarHostState.showSnackbar(updatedMessage, duration = SnackbarDuration.Short)
                is MaskedEmailDetailEvent.Deleted -> {
                    // The list hosts the archived/undo snackbar, where the mask reappears.
                    currentOnArchived(event.id, event.previousState)
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
            dismissText = stringResource(R.string.action_cancel),
            onConfirm = {
                showArchiveDialog = false
                viewModel.delete()
            },
            onDismiss = { showArchiveDialog = false },
        )
    }

    MaskedEmailDetailContent(
        uiState = uiState,
        snackbarHostState = snackbarHostState,
        banner = {
            if (showDemoBanner) {
                DemoBanner(onSignInClick = onSignInFromBanner, modifier = Modifier.padding(horizontal = 16.dp))
            }
        },
        showBackButton = showBackButton,
        onBack = onBack,
        onRetry = { viewModel.loadEmail() },
        onReauthenticate = onReauthenticate,
        onDescriptionChange = viewModel::onDescriptionChange,
        onForDomainChange = viewModel::onForDomainChange,
        onUrlChange = viewModel::onUrlChange,
        onEnable = viewModel::enable,
        onDisable = viewModel::disable,
        onSaveChanges = viewModel::saveChanges,
        onStartEditing = viewModel::startEditing,
        onDiscardEdits = viewModel::discardEdits,
        onCopyEmail = { addr -> copyToClipboard(context, addr) },
        onArchiveClick = { showArchiveDialog = true },
    )
}

@Composable
internal fun MaskedEmailDetailContent(
    uiState: MaskedEmailDetailUiState,
    snackbarHostState: SnackbarHostState,
    banner: @Composable () -> Unit,
    onBack: () -> Unit,
    onRetry: () -> Unit,
    onReauthenticate: () -> Unit,
    onDescriptionChange: (String) -> Unit,
    onForDomainChange: (String) -> Unit,
    onUrlChange: (String) -> Unit,
    onEnable: () -> Unit,
    onDisable: () -> Unit,
    onSaveChanges: () -> Unit,
    onStartEditing: () -> Unit,
    onDiscardEdits: () -> Unit,
    onCopyEmail: (String) -> Unit,
    onArchiveClick: () -> Unit,
    showBackButton: Boolean = true,
) {
    val scrollState = rememberScrollState()
    val revealPx = with(LocalDensity.current) { TITLE_REVEAL_SCROLL_DP.dp.toPx() }
    val titleVisible by remember(revealPx) { derivedStateOf { scrollState.value > revealPx } }
    val barRaised by remember { derivedStateOf { scrollState.value > 0 } }
    val motion = rememberMotion()

    val titleAlpha by animateFloatAsState(
        targetValue = if (titleVisible) 1f else 0f,
        animationSpec = motion.defaultEffects,
        label = "titleAlpha",
    )
    val titleLiftOffsetPx = with(LocalDensity.current) { 8.dp.toPx() }
    val titleLiftPx by animateFloatAsState(
        targetValue = if (titleVisible) 0f else titleLiftOffsetPx,
        animationSpec = motion.defaultSpatial,
        label = "titleLift",
    )

    val email = uiState.email
    val backDesc = stringResource(R.string.navigate_back)

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.surface,
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
        topBar = {
            ScrollRaisedTopBar(
                raised = barRaised,
                title = {
                    Text(
                        text = email?.localPart.orEmpty(),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        // The hero already carries the heading, so this faded
                        // duplicate stays out of the accessibility tree.
                        modifier = Modifier
                            .graphicsLayer {
                                alpha = titleAlpha
                                translationY = titleLiftPx
                            }
                            .clearAndSetSemantics { },
                    )
                },
                navigationIcon = {
                    if (showBackButton) {
                        IconButton(onClick = onBack, shapes = IconButtonDefaults.shapes()) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = backDesc,
                            )
                        }
                    }
                },
                banner = banner,
            )
        },
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .consumeWindowInsets(paddingValues),
        ) {
            val loadMode = when {
                uiState.isLoading && email == null -> DetailLoadMode.LOADING
                uiState.error != null && email == null -> DetailLoadMode.ERROR
                email != null -> DetailLoadMode.READY
                else -> DetailLoadMode.LOADING
            }
            var lastFullError by remember { mutableStateOf(uiState.error) }
            if (uiState.error != null) lastFullError = uiState.error
            var lastEmail by remember { mutableStateOf(email) }
            if (email != null) lastEmail = email

            AnimatedContent(
                targetState = loadMode,
                transitionSpec = {
                    (fadeIn(motion.defaultEffects) +
                        slideInVertically(motion.defaultSpatialOffset) { it / 16 } +
                        scaleIn(motion.defaultSpatial, initialScale = 0.96f)) togetherWith
                        (fadeOut(motion.fastEffects) +
                            scaleOut(motion.fastSpatial, targetScale = 0.96f))
                },
                label = "detailLoadState",
                modifier = Modifier.fillMaxSize(),
            ) { mode ->
                when (mode) {
                    DetailLoadMode.LOADING -> Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center,
                    ) { LoadingIndicator() }

                    DetailLoadMode.ERROR -> lastFullError?.let { error ->
                        ErrorState(
                            title = R.string.email_detail_error_load,
                            error = error,
                            onRetry = onRetry,
                            onReauthenticate = onReauthenticate,
                            heroShape = MaterialShapes.SoftBurst.toShape(),
                            icon = Icons.Filled.ErrorOutline,
                        )
                    }

                    DetailLoadMode.READY -> lastEmail?.let { readyEmail ->
                        DetailBody(
                            uiState = uiState,
                            email = readyEmail,
                            scrollState = scrollState,
                            onDescriptionChange = onDescriptionChange,
                            onForDomainChange = onForDomainChange,
                            onUrlChange = onUrlChange,
                            onEnable = onEnable,
                            onDisable = onDisable,
                            onSaveChanges = onSaveChanges,
                            onRetry = onRetry,
                            onReauthenticate = onReauthenticate,
                            onStartEditing = onStartEditing,
                            onDiscardEdits = onDiscardEdits,
                            onCopyEmail = onCopyEmail,
                            onArchiveClick = onArchiveClick,
                        )
                    }
                }
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
    onRetry: () -> Unit,
    onReauthenticate: () -> Unit,
    onStartEditing: () -> Unit,
    onDiscardEdits: () -> Unit,
    onCopyEmail: (String) -> Unit,
    onArchiveClick: () -> Unit,
) {
    val haptic = LocalHapticFeedback.current
    val motion = rememberMotion()
    val editing = uiState.isEditing
    val saving = uiState.updateOperation == MaskedEmailDetailUpdate.SAVE_CHANGES

    var copyNonce by remember { mutableIntStateOf(0) }
    var copied by remember { mutableStateOf(false) }
    LaunchedEffect(copyNonce) {
        if (copyNonce > 0) {
            copied = true
            delay(COPIED_FEEDBACK_MS)
            copied = false
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .imePadding()
            .verticalScroll(scrollState)
            .readableWidth(ContentWidth.Form)
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
                onEnable = onEnable,
                onDisable = onDisable,
            )
        }

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
            val editRotate by animateFloatAsState(
                targetValue = if (editing) 90f else 0f,
                animationSpec = motion.fastSpatial,
                label = "editRotate",
            )
            IconButton(
                onClick = {
                    if (editing) onDiscardEdits() else onStartEditing()
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
                    modifier = Modifier.graphicsLayer { rotationZ = editRotate },
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
                    val enteringEdit = targetState
                    val enterSlide = slideInVertically(motion.defaultSpatialOffset) { full ->
                        if (enteringEdit) full / 12 else -full / 12
                    }
                    val exitSlide = slideOutVertically(motion.fastSpatialOffset) { full ->
                        if (enteringEdit) -full / 12 else full / 12
                    }
                    (enterSlide +
                        fadeIn(motion.defaultEffects) +
                        scaleIn(motion.defaultSpatial, initialScale = 0.98f)) togetherWith
                        (exitSlide +
                            fadeOut(motion.fastEffects) +
                            scaleOut(motion.fastSpatial, targetScale = 0.98f)) using
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

        var lastInlineError by remember { mutableStateOf(uiState.error) }
        if (uiState.error != null) lastInlineError = uiState.error
        AnimatedVisibility(
            visible = uiState.error != null,
            enter = fadeIn(motion.defaultEffects) + expandVertically(motion.defaultSpatialSize),
            exit = fadeOut(motion.fastEffects) + shrinkVertically(motion.fastSpatialSize),
        ) {
            lastInlineError?.let { error ->
                InlineErrorBanner(
                    error = error,
                    onRetry = onRetry,
                    onReauthenticate = onReauthenticate,
                    modifier = Modifier
                        .padding(horizontal = 16.dp)
                        .padding(top = 12.dp),
                )
            }
        }

        AnimatedVisibility(
            visible = editing,
            enter = fadeIn(motion.defaultEffects) + expandVertically(motion.defaultSpatialSize),
            exit = fadeOut(motion.fastEffects) + shrinkVertically(motion.fastSpatialSize),
        ) {
            EditActions(
                saving = saving,
                busy = uiState.isUpdating,
                canSave = uiState.hasChanges,
                onCancel = onDiscardEdits,
                onSave = onSaveChanges,
                modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 14.dp),
            )
        }

        AnimatedVisibility(
            visible = email.state != EmailState.DELETED,
            enter = fadeIn(motion.defaultEffects) + expandVertically(motion.defaultSpatialSize),
            exit = fadeOut(motion.fastEffects) + shrinkVertically(motion.fastSpatialSize),
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

private enum class DetailLoadMode { LOADING, ERROR, READY }






