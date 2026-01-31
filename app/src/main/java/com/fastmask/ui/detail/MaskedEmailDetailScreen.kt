package com.fastmask.ui.detail

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.fastmask.domain.model.EmailState
import com.fastmask.ui.components.ErrorMessage
import com.fastmask.ui.components.LoadingIndicator
import com.fastmask.ui.theme.DeletedRed
import com.fastmask.ui.theme.DisabledGray
import com.fastmask.ui.theme.EnabledGreen
import com.fastmask.ui.theme.PendingOrange
import kotlinx.coroutines.flow.collectLatest

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MaskedEmailDetailScreen(
    onNavigateBack: () -> Unit,
    viewModel: MaskedEmailDetailViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    var showDeleteDialog by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        viewModel.events.collectLatest { event ->
            when (event) {
                is MaskedEmailDetailEvent.Updated -> {
                    Toast.makeText(context, "Updated successfully", Toast.LENGTH_SHORT).show()
                }
                is MaskedEmailDetailEvent.Deleted -> {
                    Toast.makeText(context, "Deleted successfully", Toast.LENGTH_SHORT).show()
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
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                },
                actions = {
                    if (uiState.email != null) {
                        IconButton(onClick = { showDeleteDialog = true }) {
                            Icon(
                                imageVector = Icons.Default.Delete,
                                contentDescription = "Delete",
                                tint = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                )
            )
        }
    ) { paddingValues ->
        when {
            uiState.isLoading && uiState.email == null -> {
                LoadingIndicator(
                    modifier = Modifier.padding(paddingValues)
                )
            }

            uiState.error != null && uiState.email == null -> {
                ErrorMessage(
                    message = uiState.error!!,
                    onRetry = viewModel::loadEmail,
                    modifier = Modifier.padding(paddingValues)
                )
            }

            uiState.email != null -> {
                EmailDetailContent(
                    uiState = uiState,
                    onDescriptionChange = viewModel::onDescriptionChange,
                    onForDomainChange = viewModel::onForDomainChange,
                    onUrlChange = viewModel::onUrlChange,
                    onToggleState = viewModel::toggleState,
                    onSaveChanges = viewModel::saveChanges,
                    modifier = Modifier.padding(paddingValues)
                )
            }
        }
    }
}

@Composable
private fun EmailDetailContent(
    uiState: MaskedEmailDetailUiState,
    onDescriptionChange: (String) -> Unit,
    onForDomainChange: (String) -> Unit,
    onUrlChange: (String) -> Unit,
    onToggleState: () -> Unit,
    onSaveChanges: () -> Unit,
    modifier: Modifier = Modifier
) {
    val email = uiState.email!!
    val context = LocalContext.current

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState())
    ) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant
            )
        ) {
            Column(
                modifier = Modifier.padding(16.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Email Address",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = email.email,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    IconButton(
                        onClick = {
                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            val clip = ClipData.newPlainText("Email", email.email)
                            clipboard.setPrimaryClip(clip)
                            Toast.makeText(context, "Copied to clipboard", Toast.LENGTH_SHORT).show()
                        }
                    ) {
                        Icon(
                            imageVector = Icons.Default.ContentCopy,
                            contentDescription = "Copy email"
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
                    val (statusText, statusColor) = when (email.state) {
                        EmailState.ENABLED -> "Enabled" to EnabledGreen
                        EmailState.DISABLED -> "Disabled" to DisabledGray
                        EmailState.DELETED -> "Deleted" to DeletedRed
                        EmailState.PENDING -> "Pending" to PendingOrange
                    }
                    Text(
                        text = statusText,
                        style = MaterialTheme.typography.bodyMedium,
                        color = statusColor,
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
                    onClick = onToggleState,
                    modifier = Modifier.weight(1f),
                    enabled = !uiState.isUpdating,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = EnabledGreen
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
                    onClick = onToggleState,
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
            onClick = onSaveChanges,
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
