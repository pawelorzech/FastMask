package com.fastmask.ui.create

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
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.fastmask.R
import com.fastmask.domain.model.EmailState
import com.fastmask.ui.components.DashedDesignCard
import com.fastmask.ui.components.DesignInput
import com.fastmask.ui.components.MonoLabel
import com.fastmask.ui.components.PillButton
import com.fastmask.ui.components.PillButtonVariant
import com.fastmask.ui.components.PillIconButton
import com.fastmask.ui.components.StateDot
import com.fastmask.ui.theme.FastMaskExtras
import com.fastmask.ui.theme.JetBrainsMono
import kotlinx.coroutines.flow.collectLatest

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreateMaskedEmailScreen(
    onNavigateBack: () -> Unit,
    viewModel: CreateMaskedEmailViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    val extras = FastMaskExtras.current

    val createdMessageTemplate = stringResource(R.string.create_email_created)
    val copyAction = stringResource(R.string.create_email_copy_action)
    val backDesc = stringResource(R.string.navigate_back)

    LaunchedEffect(Unit) {
        viewModel.events.collectLatest { event ->
            when (event) {
                is CreateMaskedEmailEvent.Created -> {
                    val msg = createdMessageTemplate.replace("%s", event.email)
                    val result = snackbarHostState.showSnackbar(
                        message = msg,
                        actionLabel = copyAction,
                        duration = SnackbarDuration.Long,
                    )
                    if (result == SnackbarResult.ActionPerformed) {
                        copyToClipboard(context, event.email)
                    }
                    onNavigateBack()
                }
            }
        }
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
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                PillIconButton(onClick = onNavigateBack, contentDescription = backDesc) {
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
                    hint = uiState.prefixError ?: stringResource(R.string.create_email_prefix_hint),
                    isError = uiState.prefixError != null,
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

                if (uiState.error != null) {
                    Spacer(Modifier.height(16.dp))
                    Text(
                        text = uiState.error!!,
                        style = MaterialTheme.typography.bodySmall,
                        color = extras.status.deleted.content,
                    )
                }

                Spacer(Modifier.height(28.dp))

                PillButton(
                    text = if (uiState.isLoading) "…" else stringResource(R.string.create_email_button),
                    onClick = viewModel::create,
                    variant = PillButtonVariant.Primary,
                    enabled = !uiState.isLoading && uiState.prefixError == null,
                    fullWidth = true,
                    trailing = if (uiState.isLoading) {
                        {
                            CircularProgressIndicator(
                                modifier = Modifier.size(14.dp),
                                color = extras.onAccent,
                                strokeWidth = 2.dp,
                            )
                        }
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
                    .clickable(enabled = enabled) { onSelect(state) }
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

private fun copyToClipboard(context: Context, value: String) {
    val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    cm.setPrimaryClip(ClipData.newPlainText("Email", value))
}
