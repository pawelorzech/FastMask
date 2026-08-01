package com.fastmask.ui.create

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.selection.selectable
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.activity.compose.BackHandler
import androidx.compose.material3.AlertDialog
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.fastmask.R
import com.fastmask.domain.model.EmailState
import com.fastmask.domain.share.SharePrefill
import com.fastmask.ui.components.ConfirmDialog
import com.fastmask.ui.components.DashedDesignCard
import com.fastmask.ui.components.DemoBanner
import com.fastmask.ui.components.DesignInput
import com.fastmask.ui.components.MonoLabel
import com.fastmask.ui.components.PillButton
import com.fastmask.ui.components.PillButtonVariant
import com.fastmask.ui.components.PillIconButton
import com.fastmask.ui.components.StateDot
import com.fastmask.ui.theme.FastMaskExtras
import com.fastmask.ui.theme.JetBrainsMono
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.layout.heightIn
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.heading

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreateMaskedEmailScreen(
    onNavigateBack: () -> Unit,
    /** The mask exists; hand its address to the list, which reports it there. */
    onCreated: (String) -> Unit,
    onSignInFromBanner: () -> Unit,
    prefill: SharePrefill? = null,
    viewModel: CreateMaskedEmailViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val extras = FastMaskExtras.current

    // Guard against losing typed-but-uncreated input on back / swipe.
    val isDirty = uiState.emailPrefix.isNotEmpty() || uiState.forDomain.isNotEmpty() ||
        uiState.description.isNotEmpty() || uiState.url.isNotEmpty()
    var showDiscardDialog by remember { mutableStateOf(false) }
    val onBack = { if (isDirty) showDiscardDialog = true else onNavigateBack() }
    BackHandler(enabled = isDirty) { showDiscardDialog = true }
    if (showDiscardDialog) {
        DiscardChangesDialog(
            onConfirm = { showDiscardDialog = false; onNavigateBack() },
            onDismiss = { showDiscardDialog = false },
        )
    }

    val backDesc = stringResource(R.string.navigate_back)

    LaunchedEffect(Unit) {
        viewModel.applyPrefill(prefill)
    }

    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                // Leave immediately. This used to await the confirmation
                // snackbar before navigating, and showSnackbar suspends for its
                // full Long duration (~10 s) unless the action is tapped — so
                // the form sat there looking frozen after a successful create.
                // The list shows the confirmation instead, with the same Copy
                // action, which is also where the new mask now is.
                is CreateMaskedEmailEvent.Created -> onCreated(event.email)
            }
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
        ) {
            // Demo mode banner (auto-hides in REAL mode).
            DemoBanner(onSignInClick = onSignInFromBanner)

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                PillIconButton(onClick = onBack, contentDescription = backDesc) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                }
            }

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 24.dp)
                    .padding(top = 8.dp, bottom = 32.dp),
            ) {
                Text(
                    text = stringResource(R.string.create_email_title),
                    style = MaterialTheme.typography.displayMedium,
                    modifier = Modifier.semantics { heading() },
                    color = MaterialTheme.colorScheme.onBackground,
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    text = stringResource(R.string.create_email_subtitle),
                    style = MaterialTheme.typography.bodyMedium,
                    color = extras.inkSoft,
                )
                Spacer(Modifier.height(24.dp))

                // Preview card
                DashedDesignCard(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp)) {
                        MonoLabel(text = stringResource(R.string.create_email_preview_label))
                        Spacer(Modifier.height(6.dp))
                        val prefix = uiState.emailPrefix.takeIf { it.isNotEmpty() }
                            ?: stringResource(R.string.create_email_preview_random)
                        val suffix = stringResource(R.string.create_email_preview_suffix)
                        val previewAnnotated = buildAnnotatedString {
                            withStyle(
                                SpanStyle(
                                    color = extras.accent,
                                    fontFamily = JetBrainsMono,
                                ),
                            ) { append(prefix) }
                            withStyle(
                                SpanStyle(
                                    color = extras.inkMuted,
                                    fontFamily = JetBrainsMono,
                                ),
                            ) { append(suffix) }
                        }
                        Text(
                            text = previewAnnotated,
                            style = MaterialTheme.typography.bodyMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }

                Spacer(Modifier.height(16.dp))

                // Form
                DesignInput(
                    value = uiState.emailPrefix,
                    onValueChange = viewModel::onPrefixChange,
                    label = stringResource(R.string.create_email_prefix_label),
                    placeholder = stringResource(R.string.create_email_prefix_placeholder),
                    hint = uiState.prefixErrorRes?.let { stringResource(it) }
                        ?: stringResource(R.string.create_email_prefix_hint),
                    isError = uiState.prefixErrorRes != null,
                    enabled = !uiState.isLoading,
                    mono = true,
                )
                Spacer(Modifier.height(14.dp))
                DesignInput(
                    value = uiState.forDomain,
                    onValueChange = viewModel::onDomainChange,
                    label = stringResource(R.string.create_email_domain_label),
                    placeholder = stringResource(R.string.create_email_domain_placeholder),
                    enabled = !uiState.isLoading,
                )
                Spacer(Modifier.height(14.dp))
                DesignInput(
                    value = uiState.description,
                    onValueChange = viewModel::onDescriptionChange,
                    label = stringResource(R.string.create_email_description_label),
                    placeholder = stringResource(R.string.create_email_description_placeholder),
                    enabled = !uiState.isLoading,
                )
                Spacer(Modifier.height(14.dp))
                DesignInput(
                    value = uiState.url,
                    onValueChange = viewModel::onUrlChange,
                    label = stringResource(R.string.create_email_url_label),
                    placeholder = stringResource(R.string.create_email_url_placeholder),
                    enabled = !uiState.isLoading,
                    mono = true,
                )

                Spacer(Modifier.height(24.dp))

                // Initial state segmented
                MonoLabel(text = stringResource(R.string.create_email_initial_state))
                Spacer(Modifier.height(10.dp))
                StateSegmented(
                    selected = uiState.initialState,
                    onSelect = viewModel::onStateChange,
                    enabled = !uiState.isLoading,
                )

                if (uiState.errorRes != null) {
                    Spacer(Modifier.height(16.dp))
                    Text(
                        text = stringResource(uiState.errorRes!!),
                        style = MaterialTheme.typography.bodySmall,
                        color = extras.status.deleted.content,
                    )
                }

                Spacer(Modifier.height(28.dp))

                PillButton(
                    text = stringResource(R.string.create_email_button),
                    loading = uiState.isLoading,
                    loadingDescription = stringResource(R.string.state_working),
                    onClick = viewModel::create,
                    variant = PillButtonVariant.Primary,
                    enabled = uiState.prefixErrorRes == null,
                    fullWidth = true,
                    trailing = if (uiState.isLoading) {
                        null
                    } else {
                        {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                                contentDescription = null,
                                tint = extras.onAccent,
                                modifier = Modifier.size(16.dp),
                            )
                        }
                    },
                )
            }
        }
    }
}

@Composable
private fun DiscardChangesDialog(onConfirm: () -> Unit, onDismiss: () -> Unit) {
    ConfirmDialog(
        title = stringResource(R.string.discard_changes_title),
        message = stringResource(R.string.discard_changes_message),
        confirmText = stringResource(R.string.discard_changes_confirm),
        dismissText = stringResource(R.string.discard_changes_cancel),
        onConfirm = onConfirm,
        onDismiss = onDismiss,
    )
}

@Composable
private fun StateSegmented(
    selected: EmailState,
    onSelect: (EmailState) -> Unit,
    enabled: Boolean,
) {
    val extras = FastMaskExtras.current
    val shape = RoundedCornerShape(12.dp)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(MaterialTheme.colorScheme.surface, shape)
            .border(1.dp, MaterialTheme.colorScheme.outline, shape)
            .padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        listOf(EmailState.ENABLED, EmailState.DISABLED).forEach { state ->
            val isSel = state == selected
            val rowShape = RoundedCornerShape(8.dp)
            Row(
                modifier = Modifier
                    .weight(1f)
                    .clip(rowShape)
                    .background(if (isSel) extras.surfaceAlt else Color.Transparent, rowShape)
                    // selectable (not clickable) so TalkBack announces which
                    // option is active — selection is otherwise color-only.
                    .selectable(
                        selected = isSel,
                        enabled = enabled,
                        role = Role.RadioButton,
                    ) { onSelect(state) }
                    // Segment draws at ~38dp; the target stays 48dp.
                    .heightIn(min = 48.dp)
                    .wrapContentHeight(Alignment.CenterVertically)
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (isSel) StateDot(state = state, size = 8.dp)
                Text(
                    text = stringResource(if (state == EmailState.ENABLED) R.string.state_enabled else R.string.state_disabled),
                    style = MaterialTheme.typography.titleSmall,
                    color = if (isSel) MaterialTheme.colorScheme.onSurface else extras.inkMuted,
                )
            }
        }
    }
}
