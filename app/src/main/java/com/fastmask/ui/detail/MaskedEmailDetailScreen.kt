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
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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
import com.fastmask.ui.components.expressive.maskContainer
import kotlinx.coroutines.delay

/** How long the copy button shows its "Copied" confirmation. */
private const val COPIED_FEEDBACK_MS = 1800L

/** Hero scroll offset after which the app bar title fades in. */
private const val TITLE_REVEAL_SCROLL_DP = 120

@Composable
fun MaskedEmailDetailScreen(
    onNavigateBack: () -> Unit,
    onArchived: (String, EmailState) -> Unit,
    onSignInFromBanner: () -> Unit,
    onReauthenticate: () -> Unit,
    viewModel: MaskedEmailDetailViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    var showArchiveDialog by remember { mutableStateOf(false) }
    val snackbarHostState = remember { SnackbarHostState() }

    val isDirty = uiState.hasChanges
    val onBack = rememberDiscardChangesGuard(isDirty, onLeave = onNavigateBack)

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

    // The list row this screen grows out of. Taken from the route rather than the
    // loaded mask, so the shared-bounds key is stable from the first frame.
    val containerId = viewModel.emailId

    MaskedEmailDetailContent(
        uiState = uiState,
        containerId = containerId,
        snackbarHostState = snackbarHostState,
        banner = { DemoBanner(onSignInClick = onSignInFromBanner) },
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
    containerId: String?,
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
) {
    val scrollState = rememberScrollState()
    val revealPx = with(LocalDensity.current) { TITLE_REVEAL_SCROLL_DP.dp.toPx() }
    val titleVisible = scrollState.value > revealPx

    val titleAlpha by animateFloatAsState(
        targetValue = if (titleVisible) 1f else 0f,
        animationSpec = MaterialTheme.motionScheme.defaultEffectsSpec(),
        label = "titleAlpha",
    )

    val email = uiState.email
    val backDesc = stringResource(R.string.navigate_back)
    val containerModifier = if (containerId != null) Modifier.maskContainer(containerId) else Modifier

    Scaffold(
        modifier = containerModifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.surface,
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
        topBar = {
            ScrollRaisedTopBar(
                raised = scrollState.value > 0,
                title = {
                    Text(
                        text = email?.localPart.orEmpty(),
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
                banner = banner,
            )
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

                uiState.error != null && email == null -> ErrorState(
                    title = R.string.email_detail_error_load,
                    error = uiState.error,
                    onRetry = onRetry,
                    onReauthenticate = onReauthenticate,
                    heroShape = MaterialShapes.SoftBurst.toShape(),
                    icon = Icons.Filled.ErrorOutline,
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
            IconButton(
                onClick = {
                    // Closing the editor discards the draft, like Cancel.
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

        uiState.error?.let { error ->
            // No retry: a failed save or toggle is fixed by editing or tapping
            // again; only a refused token has a recovery here (sign in).
            InlineErrorBanner(
                error = error,
                onRetry = null,
                onReauthenticate = onReauthenticate,
                modifier = Modifier
                    .padding(horizontal = 16.dp)
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
                onCancel = onDiscardEdits,
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






