package com.fastmask.ui.settings

import android.content.Intent
import android.net.Uri
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Mail
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.fastmask.BuildConfig
import com.fastmask.R
import com.fastmask.domain.model.Language
import com.fastmask.ui.components.HairlineDivider
import com.fastmask.ui.components.PillButton
import com.fastmask.ui.components.PillButtonVariant
import com.fastmask.ui.components.PillIconButton
import com.fastmask.ui.theme.FastMaskExtras
import com.fastmask.ui.theme.MonoSmallStyle
import kotlinx.coroutines.flow.collectLatest

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onNavigateBack: () -> Unit,
    onLogout: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    var showLanguageDialog by remember { mutableStateOf(false) }
    val extras = FastMaskExtras.current

    LaunchedEffect(Unit) {
        viewModel.events.collectLatest { event ->
            when (event) {
                is SettingsEvent.LoggedOut -> onLogout()
            }
        }
    }

    if (showLanguageDialog) {
        LanguagePickerDialog(
            selected = uiState.selectedLanguage,
            onSelect = {
                viewModel.onLanguageSelected(it)
                showLanguageDialog = false
            },
            onDismiss = { showLanguageDialog = false },
        )
    }

    val backDesc = stringResource(R.string.navigate_back)

    Scaffold(containerColor = MaterialTheme.colorScheme.background) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
        ) {
            // Top bar (just pill back)
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
                    text = stringResource(R.string.settings_title),
                    style = MaterialTheme.typography.displayMedium,
                    color = MaterialTheme.colorScheme.onBackground,
                )
                Spacer(Modifier.height(28.dp))

                SettingsRow(
                    label = stringResource(R.string.settings_language),
                    value = uiState.selectedLanguage?.let { stringResource(it.displayNameRes) }
                        ?: stringResource(R.string.settings_system_default),
                    leading = Icons.Outlined.Language,
                    trailing = Icons.Filled.ChevronRight,
                    onClick = { showLanguageDialog = true },
                )

                SettingsRow(
                    label = stringResource(R.string.settings_contact),
                    value = stringResource(R.string.settings_contact_description),
                    leading = Icons.Filled.Mail,
                    trailing = Icons.Filled.ChevronRight,
                    onClick = {
                        val emailIntent = Intent(Intent.ACTION_SENDTO).apply {
                            data = Uri.parse("mailto:")
                            putExtra(Intent.EXTRA_EMAIL, arrayOf("pawel@orzech.me"))
                            putExtra(
                                Intent.EXTRA_SUBJECT,
                                context.getString(R.string.settings_feedback_subject),
                            )
                        }
                        if (emailIntent.resolveActivity(context.packageManager) != null) {
                            context.startActivity(emailIntent)
                        }
                    },
                )

                SettingsRow(
                    label = stringResource(R.string.settings_logout),
                    value = stringResource(R.string.settings_logout_description),
                    leading = Icons.AutoMirrored.Filled.Logout,
                    leadingTint = extras.status.deleted.content,
                    onClick = viewModel::logout,
                )

                Spacer(Modifier.height(48.dp))

                Text(
                    text = "FastMask · ${stringResource(R.string.settings_version, BuildConfig.VERSION_NAME)}",
                    style = MonoSmallStyle,
                    color = extras.inkMuted,
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                )
            }
        }
    }
}

@Composable
private fun SettingsRow(
    label: String,
    value: String,
    leading: ImageVector,
    onClick: () -> Unit,
    trailing: ImageVector? = null,
    leadingTint: Color? = null,
) {
    val extras = FastMaskExtras.current
    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onClick)
                .padding(vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Leading icon in 36×36 rounded square
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(MaterialTheme.colorScheme.surface)
                    .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(10.dp)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = leading,
                    contentDescription = null,
                    tint = leadingTint ?: extras.inkSoft,
                    modifier = Modifier.size(16.dp),
                )
            }
            Spacer(Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Medium),
                    color = MaterialTheme.colorScheme.onBackground,
                )
                Text(
                    text = value,
                    style = MaterialTheme.typography.bodySmall,
                    color = extras.inkMuted,
                )
            }
            if (trailing != null) {
                Icon(
                    imageVector = trailing,
                    contentDescription = null,
                    tint = extras.inkMuted,
                    modifier = Modifier.size(16.dp),
                )
            }
        }
        HairlineDivider()
    }
}

@Composable
private fun LanguagePickerDialog(
    selected: Language?,
    onSelect: (Language?) -> Unit,
    onDismiss: () -> Unit,
) {
    val extras = FastMaskExtras.current
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(20.dp),
        title = {
            Text(
                text = stringResource(R.string.settings_select_language),
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onSurface,
            )
        },
        text = {
            Box(modifier = Modifier.height(360.dp)) {
                LazyColumn {
                    item {
                        LanguageRow(
                            label = stringResource(R.string.settings_system_default),
                            isSelected = selected == null,
                            onClick = { onSelect(null) },
                        )
                    }
                    items(items = Language.entries.toList(), key = { it.code }) { lang ->
                        LanguageRow(
                            label = stringResource(lang.displayNameRes),
                            isSelected = selected == lang,
                            onClick = { onSelect(lang) },
                        )
                    }
                }
            }
        },
        confirmButton = {
            PillButton(
                text = stringResource(R.string.email_detail_delete_cancel),
                onClick = onDismiss,
                variant = PillButtonVariant.Ghost,
            )
        },
    )
}

@Composable
private fun LanguageRow(
    label: String,
    isSelected: Boolean,
    onClick: () -> Unit,
) {
    val extras = FastMaskExtras.current
    val rowBg = if (isSelected) extras.surfaceAlt else Color.Transparent
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(rowBg)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface,
        )
        if (isSelected) {
            Icon(
                imageVector = Icons.Filled.Check,
                contentDescription = null,
                tint = extras.accent,
                modifier = Modifier.size(16.dp),
            )
        }
    }
}
