package com.fastmask.ui.detail

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.outlined.PowerSettingsNew
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.fastmask.R
import com.fastmask.domain.model.EmailState
import com.fastmask.ui.components.DesignCard
import com.fastmask.ui.components.DesignInput
import com.fastmask.ui.components.HairlineDivider
import com.fastmask.ui.components.MonoLabel
import com.fastmask.ui.components.PillButton
import com.fastmask.ui.components.PillButtonVariant
import com.fastmask.ui.components.PillIconButton
import com.fastmask.ui.components.StatePill
import com.fastmask.ui.theme.FastMaskExtras
import com.fastmask.ui.theme.JetBrainsMono
import com.fastmask.ui.util.RelativeTime
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class, androidx.compose.animation.ExperimentalSharedTransitionApi::class)
@Composable
fun MaskedEmailDetailScreen(
    onNavigateBack: () -> Unit,
    sharedTransitionScope: androidx.compose.animation.SharedTransitionScope,
    animatedContentScope: androidx.compose.animation.AnimatedContentScope,
    viewModel: MaskedEmailDetailViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    var showDeleteDialog by remember { mutableStateOf(false) }
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val extras = FastMaskExtras.current

    val updatedMessage = stringResource(R.string.email_detail_updated)
    val deletedMessage = stringResource(R.string.email_detail_deleted)
    val copiedMessage = stringResource(R.string.email_detail_copied)
    val backDesc = stringResource(R.string.navigate_back)
    val deleteDesc = stringResource(R.string.email_detail_delete)
    val copyDesc = stringResource(R.string.email_detail_copy_email)

    LaunchedEffect(Unit) {
        viewModel.events.collectLatest { event ->
            when (event) {
                is MaskedEmailDetailEvent.Updated ->
                    snackbarHostState.showSnackbar(updatedMessage, duration = SnackbarDuration.Short)
                is MaskedEmailDetailEvent.Deleted -> {
                    snackbarHostState.showSnackbar(deletedMessage, duration = SnackbarDuration.Short)
                    onNavigateBack()
                }
            }
        }
    }

    if (showDeleteDialog) {
        ArchiveDialog(
            onConfirm = {
                showDeleteDialog = false
                viewModel.delete()
            },
            onDismiss = { showDeleteDialog = false },
        )
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
        ) {
            // Top bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                PillIconButton(onClick = onNavigateBack, contentDescription = backDesc) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                }
                if (uiState.email != null) {
                    PillIconButton(
                        onClick = { showDeleteDialog = true },
                        contentDescription = deleteDesc,
                        tint = extras.status.deleted.content,
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Delete,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                        )
                    }
                }
            }

            when {
                uiState.isLoading && uiState.email == null -> CenteredLoading()
                uiState.error != null && uiState.email == null -> ErrorMessage(uiState.error!!)
                uiState.email != null -> DetailContent(
                    uiState = uiState,
                    onDescriptionChange = viewModel::onDescriptionChange,
                    onForDomainChange = viewModel::onForDomainChange,
                    onUrlChange = viewModel::onUrlChange,
                    onToggleState = viewModel::toggleState,
                    onSaveChanges = viewModel::saveChanges,
                    onCopyEmail = { addr ->
                        copyToClipboard(context, addr)
                        scope.launch {
                            snackbarHostState.showSnackbar(copiedMessage, duration = SnackbarDuration.Short)
                        }
                    },
                    copyDesc = copyDesc,
                )
            }
        }
    }
}

private fun copyToClipboard(context: Context, value: String) {
    val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    cm.setPrimaryClip(ClipData.newPlainText("Email", value))
}

@Composable
private fun DetailContent(
    uiState: MaskedEmailDetailUiState,
    onDescriptionChange: (String) -> Unit,
    onForDomainChange: (String) -> Unit,
    onUrlChange: (String) -> Unit,
    onToggleState: () -> Unit,
    onSaveChanges: () -> Unit,
    onCopyEmail: (String) -> Unit,
    copyDesc: String,
) {
    val email = uiState.email!!
    val context = LocalContext.current
    val extras = FastMaskExtras.current
    val haptic = LocalHapticFeedback.current

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp)
            .padding(top = 8.dp, bottom = 32.dp),
    ) {
        // Hero
        StatePill(
            state = email.state,
            label = stringResource(stateLabel(email.state)),
        )
        Spacer(Modifier.height(14.dp))
        Text(
            text = email.displayName,
            style = MaterialTheme.typography.displaySmall,
            color = MaterialTheme.colorScheme.onBackground,
        )

        Spacer(Modifier.height(20.dp))

        // Email card with copy
        DesignCard(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = email.email,
                    style = MaterialTheme.typography.bodyMedium.copy(fontFamily = JetBrainsMono),
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .weight(1f)
                        .padding(end = 12.dp),
                )
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .background(extras.chip)
                        .clickable {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            onCopyEmail(email.email)
                        }
                        .padding(horizontal = 10.dp, vertical = 8.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Filled.ContentCopy,
                        contentDescription = copyDesc,
                        modifier = Modifier.size(14.dp),
                    )
                }
            }
        }

        Spacer(Modifier.height(16.dp))

        // Toggle button (single)
        val isActive = email.state == EmailState.ENABLED || email.state == EmailState.PENDING
        PillButton(
            text = if (uiState.isUpdating) "…" else stringResource(if (isActive) R.string.email_detail_disable else R.string.email_detail_enable),
            onClick = onToggleState,
            variant = if (isActive) PillButtonVariant.Ghost else PillButtonVariant.Active,
            enabled = !uiState.isUpdating,
            fullWidth = true,
            leading = if (uiState.isUpdating) {
                {
                    CircularProgressIndicator(
                        modifier = Modifier.size(14.dp),
                        strokeWidth = 2.dp,
                    )
                }
            } else {
                {
                    Icon(
                        imageVector = if (isActive) Icons.Outlined.PowerSettingsNew else Icons.Filled.Check,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                    )
                }
            },
        )

        Spacer(Modifier.height(24.dp))

        HairlineDivider()
        Spacer(Modifier.height(8.dp))

        // Metadata rows
        MetaRow(label = stringResource(R.string.email_detail_created_by), value = email.createdBy ?: "—", mono = true)
        MetaRow(label = stringResource(R.string.email_detail_created), value = email.createdAt?.let { RelativeTime.full(it) } ?: "—", mono = true)
        MetaRow(label = stringResource(R.string.email_detail_last_message), value = email.lastMessageAt?.let { RelativeTime.full(it) } ?: stringResource(R.string.time_never), mono = true)

        Spacer(Modifier.height(28.dp))

        // Edit section
        MonoLabel(text = stringResource(R.string.email_detail_edit_title))
        Spacer(Modifier.height(16.dp))

        DesignInput(
            value = uiState.editedDescription,
            onValueChange = onDescriptionChange,
            label = stringResource(R.string.email_detail_description_label),
            enabled = !uiState.isUpdating,
        )
        Spacer(Modifier.height(14.dp))

        DesignInput(
            value = uiState.editedForDomain,
            onValueChange = onForDomainChange,
            label = stringResource(R.string.email_detail_domain_label),
            enabled = !uiState.isUpdating,
        )
        Spacer(Modifier.height(14.dp))

        DesignInput(
            value = uiState.editedUrl,
            onValueChange = onUrlChange,
            label = stringResource(R.string.email_detail_url_label),
            mono = true,
            enabled = !uiState.isUpdating,
        )

        if (uiState.error != null) {
            Spacer(Modifier.height(14.dp))
            Text(
                text = uiState.error!!,
                style = MaterialTheme.typography.bodySmall,
                color = extras.status.deleted.content,
            )
        }

        Spacer(Modifier.height(16.dp))

        val email1 = uiState.email
        val hasChanges = email1 != null && (
            uiState.editedDescription != (email1.description ?: "") ||
                uiState.editedForDomain != (email1.forDomain ?: "") ||
                uiState.editedUrl != (email1.url ?: "")
            )

        PillButton(
            text = if (uiState.isUpdating) "…" else stringResource(R.string.email_detail_save),
            onClick = onSaveChanges,
            variant = PillButtonVariant.Secondary,
            enabled = hasChanges && !uiState.isUpdating,
            fullWidth = true,
        )
    }
}

@Composable
private fun MetaRow(label: String, value: String, mono: Boolean = false) {
    val extras = FastMaskExtras.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        MonoLabel(text = label)
        Spacer(Modifier.width(12.dp))
        Text(
            text = value,
            style = if (mono) MaterialTheme.typography.bodySmall.copy(fontFamily = JetBrainsMono)
                   else MaterialTheme.typography.bodySmall,
            color = extras.inkSoft,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f, fill = false),
            textAlign = androidx.compose.ui.text.style.TextAlign.End,
        )
    }
}

@Composable
private fun ArchiveDialog(onConfirm: () -> Unit, onDismiss: () -> Unit) {
    val extras = FastMaskExtras.current
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(20.dp),
        title = {
            Text(
                text = stringResource(R.string.email_detail_delete_dialog_title),
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onSurface,
            )
        },
        text = {
            Text(
                text = stringResource(R.string.email_detail_delete_dialog_message),
                style = MaterialTheme.typography.bodyMedium,
                color = extras.inkSoft,
            )
        },
        confirmButton = {
            PillButton(
                text = stringResource(R.string.email_detail_delete_confirm),
                onClick = onConfirm,
                variant = PillButtonVariant.Danger,
            )
        },
        dismissButton = {
            PillButton(
                text = stringResource(R.string.email_detail_delete_cancel),
                onClick = onDismiss,
                variant = PillButtonVariant.Ghost,
            )
        },
    )
}

@Composable
private fun CenteredLoading() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        CircularProgressIndicator(
            modifier = Modifier.size(32.dp),
            color = FastMaskExtras.current.accent,
            strokeWidth = 2.dp,
        )
    }
}

@Composable
private fun ErrorMessage(message: String) {
    val extras = FastMaskExtras.current
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = stringResource(R.string.email_detail_error_load),
            style = MaterialTheme.typography.headlineSmall,
            color = extras.inkSoft,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = message,
            style = MaterialTheme.typography.bodySmall,
            color = extras.inkMuted,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
        )
    }
}

private fun stateLabel(state: EmailState) = when (state) {
    EmailState.ENABLED -> R.string.state_enabled
    EmailState.DISABLED -> R.string.state_disabled
    EmailState.DELETED -> R.string.state_deleted
    EmailState.PENDING -> R.string.state_pending
}
