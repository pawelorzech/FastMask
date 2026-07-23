package com.fastmask.ui.settings

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
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
import androidx.compose.material.icons.automirrored.filled.Login
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.Mail
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.WorkspacePremium
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.core.content.FileProvider
import com.fastmask.BuildConfig
import com.fastmask.R
import com.fastmask.domain.model.Accent
import com.fastmask.domain.model.AppMode
import com.fastmask.domain.model.Language
import com.fastmask.domain.model.ProStatus
import com.fastmask.ui.components.HairlineDivider
import com.fastmask.ui.components.MonoLabel
import com.fastmask.ui.components.PillButton
import com.fastmask.ui.components.PillButtonVariant
import com.fastmask.ui.components.PillIconButton
import com.fastmask.ui.lock.canUseAppLock
import com.fastmask.ui.theme.FastMaskExtras
import com.fastmask.ui.theme.MonoSmallStyle
import com.fastmask.ui.theme.color
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onNavigateBack: () -> Unit,
    onLogout: () -> Unit,
    onSignInFromDemo: () -> Unit,
    onNavigateToPro: (String) -> Unit,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()
    val appMode by viewModel.appMode.collectAsState()
    val proStatus by viewModel.proStatus.collectAsState()
    val selectedAccent by viewModel.accent.collectAsState()
    val appLockEnabled by viewModel.appLockEnabled.collectAsState()
    val context = LocalContext.current
    var showLanguageDialog by remember { mutableStateOf(false) }
    var showLockUnavailableDialog by remember { mutableStateOf(false) }
    val extras = FastMaskExtras.current
    val snackbarHostState = remember { SnackbarHostState() }
    val exportFailedMessage = stringResource(R.string.settings_export_failed)
    val exportChooserTitle = stringResource(R.string.settings_export_title)

    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                is SettingsEvent.LoggedOut -> onLogout()
                is SettingsEvent.GoToSignIn -> onSignInFromDemo()
                is SettingsEvent.OpenPro -> onNavigateToPro(event.source)
                is SettingsEvent.ExportFailed -> snackbarHostState.showSnackbar(exportFailedMessage)
                is SettingsEvent.ShareCsv -> {
                    withContext(Dispatchers.IO) {
                        runCatching {
                            val dir = File(context.cacheDir, "exports").apply { mkdirs() }
                            // Previous exports are stale the moment a new one is
                            // requested — keep the cache dir at a single file.
                            dir.listFiles()?.forEach { it.delete() }
                            val file = File(dir, "fastmask-masks.csv")
                            file.writeText(event.csv)
                            FileProvider.getUriForFile(
                                context,
                                "${BuildConfig.APPLICATION_ID}.fileprovider",
                                file,
                            )
                        }
                    }.onSuccess { uri ->
                        val send = Intent(Intent.ACTION_SEND).apply {
                            type = "text/csv"
                            putExtra(Intent.EXTRA_STREAM, uri)
                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        }
                        context.startActivity(Intent.createChooser(send, exportChooserTitle))
                    }.onFailure {
                        snackbarHostState.showSnackbar(exportFailedMessage)
                    }
                }
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

    if (uiState.showAccentDialog) {
        AccentPickerDialog(
            selected = selectedAccent,
            onSelect = viewModel::onAccentSelected,
            onDismiss = viewModel::onAccentDialogDismissed,
        )
    }

    if (showLockUnavailableDialog) {
        AlertDialog(
            onDismissRequest = { showLockUnavailableDialog = false },
            containerColor = MaterialTheme.colorScheme.surface,
            shape = RoundedCornerShape(20.dp),
            title = {
                Text(
                    text = stringResource(R.string.settings_app_lock),
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            },
            text = {
                Text(
                    text = stringResource(R.string.settings_app_lock_unavailable),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            },
            confirmButton = {
                PillButton(
                    text = stringResource(R.string.email_detail_delete_cancel),
                    onClick = { showLockUnavailableDialog = false },
                    variant = PillButtonVariant.Ghost,
                )
            },
        )
    }

    val backDesc = stringResource(R.string.navigate_back)

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { paddingValues ->
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

                // Demo-mode "Account" section — only visible while AppMode.DEMO is active.
                if (appMode == AppMode.DEMO) {
                    MonoLabel(text = stringResource(R.string.settings_demo_section))
                    Spacer(Modifier.height(10.dp))
                    Text(
                        text = stringResource(R.string.settings_demo_description),
                        style = MaterialTheme.typography.bodyMedium,
                        color = extras.inkSoft,
                    )
                    Spacer(Modifier.height(14.dp))
                    SettingsRow(
                        label = stringResource(R.string.settings_signin_with_fastmail),
                        value = stringResource(R.string.welcome_tagline),
                        leading = Icons.AutoMirrored.Filled.Login,
                        leadingTint = extras.accent,
                        trailing = Icons.Filled.ChevronRight,
                        onClick = viewModel::exitDemoMode,
                    )
                    Spacer(Modifier.height(8.dp))
                }

                if (BuildConfig.MONETIZATION_ENABLED) {
                    val isPro = proStatus == ProStatus.PRO
                    MonoLabel(text = stringResource(R.string.settings_pro_section))
                    Spacer(Modifier.height(4.dp))
                    SettingsRow(
                        label = stringResource(R.string.settings_pro_row_title),
                        value = if (isPro) {
                            stringResource(R.string.settings_pro_value_active)
                        } else {
                            stringResource(R.string.settings_pro_value_free)
                        },
                        leading = Icons.Filled.WorkspacePremium,
                        leadingTint = extras.accent,
                        trailing = Icons.Filled.ChevronRight,
                        onClick = viewModel::onProRowClick,
                    )
                    SettingsRow(
                        label = stringResource(R.string.settings_accent),
                        value = if (isPro) {
                            stringResource(selectedAccent.displayNameRes)
                        } else {
                            stringResource(R.string.settings_accent_locked)
                        },
                        leading = Icons.Filled.Palette,
                        trailing = Icons.Filled.ChevronRight,
                        onClick = viewModel::onAccentClick,
                    )
                    SettingsToggleRow(
                        label = stringResource(R.string.settings_app_lock),
                        value = if (isPro) {
                            stringResource(R.string.settings_app_lock_description)
                        } else {
                            stringResource(R.string.settings_accent_locked)
                        },
                        leading = Icons.Filled.Fingerprint,
                        checked = appLockEnabled && isPro,
                        onToggle = { enabled ->
                            if (enabled && isPro && !canUseAppLock(context)) {
                                showLockUnavailableDialog = true
                            } else {
                                viewModel.onAppLockToggled(enabled)
                            }
                        },
                    )
                    SettingsRow(
                        label = stringResource(R.string.settings_export_title),
                        value = if (isPro) {
                            stringResource(R.string.settings_export_description)
                        } else {
                            stringResource(R.string.settings_accent_locked)
                        },
                        leading = Icons.Filled.FileDownload,
                        trailing = Icons.Filled.ChevronRight,
                        onClick = viewModel::onExportClick,
                    )
                    Spacer(Modifier.height(24.dp))
                }

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
                // Every trailing icon today is the directional ChevronRight,
                // which has no AutoMirrored variant in this icons version —
                // mirror it manually so RTL (Arabic) points "deeper", not back.
                val isRtl = LocalLayoutDirection.current == LayoutDirection.Rtl
                Icon(
                    imageVector = trailing,
                    contentDescription = null,
                    tint = extras.inkMuted,
                    modifier = Modifier
                        .size(16.dp)
                        .graphicsLayer { if (isRtl) scaleX = -1f },
                )
            }
        }
        HairlineDivider()
    }
}

@Composable
private fun SettingsToggleRow(
    label: String,
    value: String,
    leading: ImageVector,
    checked: Boolean,
    onToggle: (Boolean) -> Unit,
) {
    val extras = FastMaskExtras.current
    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                // One toggleable row with a Switch role — a clickable row plus
                // an independently-clickable Switch gives TalkBack two focus
                // targets for a single setting and announces no state on the row.
                .toggleable(value = checked, role = Role.Switch, onValueChange = onToggle)
                .padding(vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
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
                    tint = extras.inkSoft,
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
            Switch(
                checked = checked,
                // Handled by the row's toggleable; null keeps the Switch a
                // purely visual indicator instead of a second a11y target.
                onCheckedChange = null,
            )
        }
        HairlineDivider()
    }
}

internal val Accent.displayNameRes: Int
    get() = when (this) {
        Accent.AMBER -> R.string.accent_amber
        Accent.INK -> R.string.accent_ink
        Accent.SAGE -> R.string.accent_sage
        Accent.PLUM -> R.string.accent_plum
        Accent.COBALT -> R.string.accent_cobalt
    }

@Composable
private fun AccentPickerDialog(
    selected: Accent,
    onSelect: (Accent) -> Unit,
    onDismiss: () -> Unit,
) {
    val extras = FastMaskExtras.current
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(20.dp),
        title = {
            Text(
                text = stringResource(R.string.settings_select_accent),
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onSurface,
            )
        },
        text = {
            Column {
                Accent.entries.forEach { accent ->
                    val rowBg = if (accent == selected) extras.surfaceAlt else Color.Transparent
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(rowBg)
                            // selectable + RadioButton role: without it TalkBack
                            // reads five identical rows with no way to tell
                            // which accent is currently active.
                            .selectable(
                                selected = accent == selected,
                                role = Role.RadioButton,
                                onClick = { onSelect(accent) },
                            )
                            .padding(horizontal = 12.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(
                            modifier = Modifier
                                .size(20.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(accent.color(isSystemInDarkTheme())),
                        )
                        Spacer(Modifier.width(12.dp))
                        Text(
                            text = stringResource(accent.displayNameRes),
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.weight(1f),
                        )
                        if (accent == selected) {
                            Icon(
                                imageVector = Icons.Filled.Check,
                                contentDescription = null,
                                tint = extras.accent,
                                modifier = Modifier.size(16.dp),
                            )
                        }
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
            // TalkBack must announce which language is active, not just a list
            // of identically-labelled buttons.
            .selectable(selected = isSelected, role = Role.RadioButton, onClick = onClick)
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
