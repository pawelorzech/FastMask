package com.fastmask.ui.detail

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedContentScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.HourglassEmpty
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
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
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.fastmask.domain.model.EmailState
import com.fastmask.ui.components.ErrorMessage
import com.fastmask.ui.components.LoadingIndicator
import com.fastmask.ui.theme.FastMaskStatusColors
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class, ExperimentalSharedTransitionApi::class)
@Composable
fun MaskedEmailDetailScreen(
    onNavigateBack: () -> Unit,
    sharedTransitionScope: SharedTransitionScope,
    animatedContentScope: AnimatedContentScope,
    viewModel: MaskedEmailDetailViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    var showDeleteDialog by remember { mutableStateOf(false) }
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val haptic = LocalHapticFeedback.current

    LaunchedEffect(Unit) {
        viewModel.events.collectLatest { event ->
            when (event) {
                is MaskedEmailDetailEvent.Updated -> {
                    snackbarHostState.showSnackbar(
                        message = "Updated successfully",
                        duration = SnackbarDuration.Short
                    )
                }
                is MaskedEmailDetailEvent.Deleted -> {
                    snackbarHostState.showSnackbar(
                        message = "Deleted successfully",
                        duration = SnackbarDuration.Short
                    )
                    onNavigateBack()
                }
            }
        }
    }

    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text("Delete Masked Email") },
            text = { Text("Are you sure you want to delete this masked email? This action cannot be undone.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDeleteDialog = false
                        viewModel.delete()
                    },
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Email Details") },
                navigationIcon = {
                    IconButton(
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            onNavigateBack()
                        },
                        modifier = Modifier.semantics {
                            contentDescription = "Navigate back"
                        }
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = null
                        )
                    }
                },
                actions = {
                    if (uiState.email != null) {
                        IconButton(
                            onClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                showDeleteDialog = true
                            },
                            modifier = Modifier.semantics {
                                contentDescription = "Delete email"
                            }
                        ) {
                            Icon(
                                imageVector = Icons.Default.Delete,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    titleContentColor = MaterialTheme.colorScheme.onSurface
                )
            )
        },
        snackbarHost = {
            SnackbarHost(hostState = snackbarHostState)
        }
    ) { paddingValues ->
        AnimatedContent(
            targetState = Triple(uiState.isLoading && uiState.email == null, uiState.error != null && uiState.email == null, uiState.email != null),
            transitionSpec = {
                fadeIn() togetherWith fadeOut()
            },
            label = "content_state"
        ) { (isLoading, hasError, hasEmail) ->
            when {
                isLoading -> {
                    LoadingIndicator(
                        modifier = Modifier.padding(paddingValues)
                    )
                }

                hasError -> {
                    ErrorMessage(
                        message = uiState.error!!,
                        onRetry = viewModel::loadEmail,
                        modifier = Modifier.padding(paddingValues)
                    )
                }

                hasEmail -> {
                    EmailDetailContent(
                        uiState = uiState,
                        onDescriptionChange = viewModel::onDescriptionChange,
                        onForDomainChange = viewModel::onForDomainChange,
                        onUrlChange = viewModel::onUrlChange,
                        onToggleState = viewModel::toggleState,
                        onSaveChanges = viewModel::saveChanges,
                        onCopyEmail = { email ->
                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            val clip = ClipData.newPlainText("Email", email)
                            clipboard.setPrimaryClip(clip)
                            scope.launch {
                                snackbarHostState.showSnackbar(
                                    message = "Copied to clipboard",
                                    duration = SnackbarDuration.Short
                                )
                            }
                        },
                        sharedTransitionScope = sharedTransitionScope,
                        animatedContentScope = animatedContentScope,
                        modifier = Modifier.padding(paddingValues)
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
private fun EmailDetailContent(
    uiState: MaskedEmailDetailUiState,
    onDescriptionChange: (String) -> Unit,
    onForDomainChange: (String) -> Unit,
    onUrlChange: (String) -> Unit,
    onToggleState: () -> Unit,
    onSaveChanges: () -> Unit,
    onCopyEmail: (String) -> Unit,
    sharedTransitionScope: SharedTransitionScope,
    animatedContentScope: AnimatedContentScope,
    modifier: Modifier = Modifier
) {
    val email = uiState.email!!
    val statusColors = FastMaskStatusColors.current
    val haptic = LocalHapticFeedback.current

    val colorPair = when (email.state) {
        EmailState.ENABLED -> statusColors.enabled
        EmailState.DISABLED -> statusColors.disabled
        EmailState.DELETED -> statusColors.deleted
        EmailState.PENDING -> statusColors.pending
    }

    with(sharedTransitionScope) {
        Column(
            modifier = modifier
                .fillMaxSize()
                .padding(16.dp)
                .verticalScroll(rememberScrollState())
        ) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .sharedBounds(
                        sharedContentState = rememberSharedContentState(key = "card-${email.id}"),
                        animatedVisibilityScope = animatedContentScope
                    ),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerLow
                )
            ) {
                Column(
                    modifier = Modifier.padding(16.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        StatusIcon(
                            state = email.state,
                            modifier = Modifier.sharedElement(
                                state = rememberSharedContentState(key = "icon-${email.id}"),
                                animatedVisibilityScope = animatedContentScope
                            )
                        )
                        Spacer(modifier = Modifier.width(16.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = email.displayName,
                                style = MaterialTheme.typography.titleMedium,
                                modifier = Modifier.sharedElement(
                                    state = rememberSharedContentState(key = "title-${email.id}"),
                                    animatedVisibilityScope = animatedContentScope
                                )
                            )
                            Text(
                                text = email.email,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.sharedElement(
                                    state = rememberSharedContentState(key = "email-${email.id}"),
                                    animatedVisibilityScope = animatedContentScope
                                )
                            )
                        }
                        IconButton(
                            onClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                onCopyEmail(email.email)
                            },
                            modifier = Modifier.semantics {
                                contentDescription = "Copy email address"
                            }
                        ) {
                            Icon(
                                imageVector = Icons.Default.ContentCopy,
                                contentDescription = null
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    Row(
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Status: ",
                            style = MaterialTheme.typography.bodyMedium
                        )
                        val statusText = when (email.state) {
                            EmailState.ENABLED -> "Enabled"
                            EmailState.DISABLED -> "Disabled"
                            EmailState.DELETED -> "Deleted"
                            EmailState.PENDING -> "Pending"
                        }
                        Text(
                            text = statusText,
                            style = MaterialTheme.typography.bodyMedium,
                            color = colorPair.content,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    email.createdBy?.let { createdBy ->
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Created by: $createdBy",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    email.formattedCreatedAt?.let { createdAt ->
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Created: $createdAt",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    email.formattedLastMessageAt?.let { lastMessage ->
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Last message: $lastMessage",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (email.state == EmailState.DISABLED || email.state == EmailState.DELETED) {
                    Button(
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            onToggleState()
                        },
                        modifier = Modifier.weight(1f),
                        enabled = !uiState.isUpdating,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = statusColors.enabled.container,
                            contentColor = statusColors.enabled.content
                        )
                    ) {
                        if (uiState.isUpdating) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                strokeWidth = 2.dp
                            )
                        } else {
                            Text("Enable")
                        }
                    }
                }
                if (email.state == EmailState.ENABLED || email.state == EmailState.PENDING) {
                    OutlinedButton(
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            onToggleState()
                        },
                        modifier = Modifier.weight(1f),
                        enabled = !uiState.isUpdating
                    ) {
                        if (uiState.isUpdating) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                strokeWidth = 2.dp
                            )
                        } else {
                            Text("Disable")
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            Text(
                text = "Edit Details",
                style = MaterialTheme.typography.titleMedium
            )

            Spacer(modifier = Modifier.height(16.dp))

            OutlinedTextField(
                value = uiState.editedDescription,
                onValueChange = onDescriptionChange,
                label = { Text("Description") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                enabled = !uiState.isUpdating
            )

            Spacer(modifier = Modifier.height(16.dp))

            OutlinedTextField(
                value = uiState.editedForDomain,
                onValueChange = onForDomainChange,
                label = { Text("Associated Domain") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                enabled = !uiState.isUpdating
            )

            Spacer(modifier = Modifier.height(16.dp))

            OutlinedTextField(
                value = uiState.editedUrl,
                onValueChange = onUrlChange,
                label = { Text("URL") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                enabled = !uiState.isUpdating
            )

            if (uiState.error != null) {
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = uiState.error!!,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            Button(
                onClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    onSaveChanges()
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = !uiState.isUpdating
            ) {
                if (uiState.isUpdating) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        color = MaterialTheme.colorScheme.onPrimary,
                        strokeWidth = 2.dp
                    )
                } else {
                    Text("Save Changes")
                }
            }
        }
    }
}

@Composable
private fun StatusIcon(
    state: EmailState,
    modifier: Modifier = Modifier
) {
    val statusColors = FastMaskStatusColors.current

    val (icon, colorPair) = when (state) {
        EmailState.ENABLED -> Icons.Default.Check to statusColors.enabled
        EmailState.DISABLED -> Icons.Default.Close to statusColors.disabled
        EmailState.DELETED -> Icons.Default.Delete to statusColors.deleted
        EmailState.PENDING -> Icons.Default.HourglassEmpty to statusColors.pending
    }

    Box(
        modifier = modifier
            .size(48.dp)
            .clip(CircleShape)
            .background(colorPair.container),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = state.name,
            modifier = Modifier.size(28.dp),
            tint = colorPair.content
        )
    }
}
