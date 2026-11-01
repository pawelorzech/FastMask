package com.fastmask.ui.settings

import android.content.Intent
import android.net.Uri
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Mail
import androidx.compose.material.icons.filled.Science
import androidx.compose.material.icons.filled.WorkspacePremium
import androidx.compose.material.icons.outlined.BugReport
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.ripple
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.hilt.navigation.compose.hiltViewModel
import com.fastmask.BuildConfig
import com.fastmask.R
import com.fastmask.domain.model.Accent
import com.fastmask.domain.model.AppMode
import com.fastmask.domain.model.Language
import com.fastmask.domain.model.ProStatus
import com.fastmask.ui.accessibility.politeLiveRegion
import com.fastmask.ui.accessibility.radioButtonGroup
import com.fastmask.ui.common.openExternalIntent
import com.fastmask.ui.components.expressive.SectionHeader
import com.fastmask.ui.components.expressive.SegmentedGroup
import com.fastmask.ui.components.expressive.SegmentedRow
import com.fastmask.ui.components.expressive.segmentedShape
import com.fastmask.ui.lock.canUseAppLock
import com.fastmask.ui.theme.FastMaskTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** Everything the settings content draws, so it can be previewed without Hilt. */
internal data class SettingsViewState(
    val appMode: AppMode = AppMode.REAL,
    val isPro: Boolean = false,
    val accent: Accent = Accent.DYNAMIC,
    val appLockEnabled: Boolean = false,
    val crashReportingEnabled: Boolean = true,
    val selectedLanguage: Language? = null,
    val exportInFlight: Boolean = false,
    val monetizationEnabled: Boolean = true,
    val versionName: String = "1.0.0",
)

internal class SettingsCallbacks(
    val onBack: () -> Unit = {},
    val onSignInFromDemo: () -> Unit = {},
    val onSignOut: () -> Unit = {},
    val onProRowClick: () -> Unit = {},
    val onAccentSelected: (Accent) -> Unit = {},
    /** The user chose "Unlock" on the locked-colors snackbar. */
    val onUnlockColors: () -> Unit = {},
    val onAppLockToggled: (Boolean) -> Unit = {},
    val onCrashReportingToggled: (Boolean) -> Unit = {},
    val onExportClick: () -> Unit = {},
    val onLanguageSelected: (Language?) -> Unit = {},
)

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
    val crashReportingEnabled by viewModel.crashReportingEnabled.collectAsState()
    val context = LocalContext.current
    val resources = LocalResources.current
    val snackbarHostState = remember { SnackbarHostState() }
    val exportWriteFailedMessage = stringResource(R.string.settings_export_failed_write)
    val exportChooserTitle = stringResource(R.string.settings_export_title)

    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                is SettingsEvent.LoggedOut -> onLogout()
                is SettingsEvent.GoToSignIn -> onSignInFromDemo()
                is SettingsEvent.OpenPro -> onNavigateToPro(event.source)
                is SettingsEvent.ExportFailed ->
                    snackbarHostState.showSnackbar(resources.getString(event.messageRes))
                is SettingsEvent.ShareCsv -> {
                    // The file is already written: producing it belongs to the
                    // ViewModel, which holds the injected ExportCache and so can
                    // abandon an export whose fetch outlived the session. All
                    // that is left here is turning it into a share intent.
                    runCatching {
                        FileProvider.getUriForFile(
                            context,
                            "${BuildConfig.APPLICATION_ID}.fileprovider",
                            event.file,
                        )
                    }.onSuccess { uri ->
                        val send = Intent(Intent.ACTION_SEND).apply {
                            type = "text/csv"
                            putExtra(Intent.EXTRA_STREAM, uri)
                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        }
                        context.startActivity(Intent.createChooser(send, exportChooserTitle))
                    }.onFailure {
                        // The file exists but no shareable URI could be built
                        // for it — it was evicted from the cache directory
                        // between the write and this line, or the provider
                        // rejected the path. Same advice as a failed write:
                        // this is not a "try again in a moment" situation.
                        snackbarHostState.showSnackbar(exportWriteFailedMessage)
                    }
                }
            }
        }
    }

    if (uiState.showExportConfirmation) {
        ExportConfirmationDialog(
            onConfirm = viewModel::onExportConfirmed,
            onDismiss = viewModel::onExportConfirmationDismissed,
        )
    }

    val isPro = proStatus == ProStatus.PRO
    SettingsContent(
        state = SettingsViewState(
            appMode = appMode,
            isPro = isPro,
            // Without Pro the theme ignores a stored seed (MainActivity), so
            // the picker shows what is actually painted.
            accent = if (isPro) selectedAccent else Accent.DYNAMIC,
            appLockEnabled = appLockEnabled,
            crashReportingEnabled = crashReportingEnabled,
            selectedLanguage = uiState.selectedLanguage,
            exportInFlight = uiState.exportInFlight,
            monetizationEnabled = BuildConfig.MONETIZATION_ENABLED,
            versionName = BuildConfig.VERSION_NAME,
        ),
        snackbarHostState = snackbarHostState,
        callbacks = SettingsCallbacks(
            onBack = onNavigateBack,
            onSignInFromDemo = viewModel::exitDemoMode,
            onSignOut = viewModel::logout,
            onProRowClick = viewModel::onProRowClick,
            onAccentSelected = viewModel::onAccentSelected,
            // Same analytics + paywall route the old locked accent row took.
            onUnlockColors = viewModel::onAccentClick,
            onAppLockToggled = viewModel::onAppLockToggled,
            onCrashReportingToggled = viewModel::onCrashReportingToggled,
            onExportClick = viewModel::onExportClick,
            onLanguageSelected = viewModel::onLanguageSelected,
        ),
    )
}

@Composable
internal fun SettingsContent(
    state: SettingsViewState,
    snackbarHostState: SnackbarHostState,
    callbacks: SettingsCallbacks,
) {
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()
    var showLanguageDialog by remember { mutableStateOf(false) }
    var showLockUnavailableDialog by remember { mutableStateOf(false) }
    var showLogoutDialog by remember { mutableStateOf(false) }

    val colorsLockedMessage = stringResource(R.string.settings_accent_locked)
    val unlockLabel = stringResource(R.string.settings_colors_unlock)
    val noMailAppMessage = stringResource(R.string.error_no_app_for_link)
    val feedbackSubject = stringResource(R.string.settings_feedback_subject)
    val backDesc = stringResource(R.string.navigate_back)
    val isPro = state.isPro

    if (showLanguageDialog) {
        LanguagePickerDialog(
            selected = state.selectedLanguage,
            onSelect = {
                callbacks.onLanguageSelected(it)
                showLanguageDialog = false
            },
            onDismiss = { showLanguageDialog = false },
        )
    }

    if (showLogoutDialog) {
        // Confirm before dropping the token — a mistap otherwise forces a full
        // re-login.
        AlertDialog(
            onDismissRequest = { showLogoutDialog = false },
            icon = { Icon(Icons.AutoMirrored.Filled.Logout, contentDescription = null) },
            title = { Text(stringResource(R.string.logout_confirm_title)) },
            text = { Text(stringResource(R.string.logout_confirm_message)) },
            confirmButton = {
                DialogTextButton(
                    text = stringResource(R.string.settings_logout),
                    color = MaterialTheme.colorScheme.error,
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.Confirm)
                        showLogoutDialog = false
                        callbacks.onSignOut()
                    },
                )
            },
            dismissButton = {
                DialogTextButton(
                    text = stringResource(R.string.email_detail_delete_cancel),
                    onClick = { showLogoutDialog = false },
                )
            },
        )
    }

    if (showLockUnavailableDialog) {
        AlertDialog(
            onDismissRequest = { showLockUnavailableDialog = false },
            icon = { Icon(Icons.Filled.Fingerprint, contentDescription = null) },
            title = { Text(stringResource(R.string.settings_app_lock)) },
            text = { Text(stringResource(R.string.settings_app_lock_unavailable)) },
            confirmButton = {
                DialogTextButton(
                    text = stringResource(R.string.action_ok),
                    onClick = { showLockUnavailableDialog = false },
                )
            },
        )
    }

    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            LargeFlexibleTopAppBar(
                title = {
                    Text(
                        text = stringResource(R.string.settings_title),
                        modifier = Modifier.semantics { heading() },
                    )
                },
                navigationIcon = {
                    IconButton(onClick = callbacks.onBack, shapes = IconButtonDefaults.shapes()) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = backDesc,
                        )
                    }
                },
                scrollBehavior = scrollBehavior,
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(rememberScrollState())
                .padding(bottom = 32.dp),
        ) {
            // --- Account ---
            SettingsSection(title = stringResource(R.string.settings_demo_section)) {
                if (state.appMode == AppMode.DEMO) {
                    add { index, count ->
                        StaticSegment(index, count) {
                            Icon(
                                imageVector = Icons.Filled.Science,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = stringResource(R.string.settings_demo_description),
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = MaterialTheme.colorScheme.onSurface,
                                )
                                Spacer(Modifier.height(12.dp))
                                Button(
                                    onClick = callbacks.onSignInFromDemo,
                                    shapes = ButtonDefaults.shapes(),
                                ) {
                                    Text(stringResource(R.string.settings_signin_with_fastmail))
                                }
                            }
                        }
                    }
                } else {
                    add { index, count ->
                        SettingsRow(
                            index = index,
                            count = count,
                            label = stringResource(R.string.settings_logout),
                            labelColor = MaterialTheme.colorScheme.error,
                            supporting = stringResource(R.string.settings_logout_description),
                            leading = {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.Logout,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.error,
                                )
                            },
                            onClick = { showLogoutDialog = true },
                        )
                    }
                }
            }

            // --- FastMask Pro ---
            if (state.monetizationEnabled) {
                SettingsSection(title = stringResource(R.string.settings_pro_section)) {
                    add { index, count ->
                        SettingsRow(
                            index = index,
                            count = count,
                            label = stringResource(R.string.settings_pro_row_title),
                            supporting = if (isPro) {
                                stringResource(R.string.settings_pro_value_active)
                            } else {
                                stringResource(R.string.settings_pro_value_free)
                            },
                            leading = { ProBadge() },
                            trailing = { ChevronIcon() },
                            onClick = callbacks.onProRowClick,
                        )
                    }
                }
            }

            // --- Appearance ---
            SettingsSection(title = stringResource(R.string.settings_appearance_section)) {
                if (state.monetizationEnabled) {
                    add { index, count ->
                        StaticSegment(index, count) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = stringResource(R.string.settings_accent),
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = MaterialTheme.colorScheme.onSurface,
                                )
                                // What is painted right now: the wallpaper, or the
                                // seed's name.
                                Text(
                                    text = stringResource(state.accent.displayNameRes),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                Spacer(Modifier.height(8.dp))
                                ColorSwatchRow(
                                    selected = state.accent,
                                    isPro = isPro,
                                    onSelect = callbacks.onAccentSelected,
                                    onLockedTap = {
                                        scope.launch {
                                            val result = snackbarHostState.showSnackbar(
                                                message = colorsLockedMessage,
                                                actionLabel = unlockLabel,
                                                duration = SnackbarDuration.Short,
                                            )
                                            if (result == SnackbarResult.ActionPerformed) {
                                                callbacks.onUnlockColors()
                                            }
                                        }
                                    },
                                )
                            }
                        }
                    }
                }
                add { index, count ->
                    SettingsRow(
                        index = index,
                        count = count,
                        label = stringResource(R.string.settings_language),
                        supporting = state.selectedLanguage?.let { stringResource(it.displayNameRes) }
                            ?: stringResource(R.string.settings_system_default),
                        leading = { Icon(Icons.Outlined.Language, contentDescription = null) },
                        trailing = { ChevronIcon() },
                        onClick = { showLanguageDialog = true },
                    )
                }
            }

            // --- Privacy ---
            SettingsSection(title = stringResource(R.string.settings_privacy_section)) {
                if (state.monetizationEnabled) {
                    add { index, count ->
                        // An armed lock describes itself even without Pro, and
                        // keeps its switch so it can always be turned off. The
                        // "Pro" placeholder is an invitation to buy, and showing
                        // it over a lock that IS armed told the user the opposite
                        // of what the app does on the next resume.
                        if (isPro || state.appLockEnabled) {
                            SettingsToggleRow(
                                index = index,
                                count = count,
                                label = stringResource(R.string.settings_app_lock),
                                supporting = stringResource(R.string.settings_app_lock_description),
                                leading = { Icon(Icons.Filled.Fingerprint, contentDescription = null) },
                                // The preference alone, matching MainActivity's
                                // gate: the switch reports the lock that is
                                // actually armed, not the entitlement behind it.
                                checked = state.appLockEnabled,
                                onCheckedChange = { enabled ->
                                    if (enabled && isPro && !canUseAppLock(context)) {
                                        showLockUnavailableDialog = true
                                    } else {
                                        callbacks.onAppLockToggled(enabled)
                                    }
                                },
                            )
                        } else {
                            SettingsRow(
                                index = index,
                                count = count,
                                label = stringResource(R.string.settings_app_lock),
                                supporting = stringResource(R.string.settings_accent_locked),
                                leading = { Icon(Icons.Filled.Fingerprint, contentDescription = null) },
                                trailing = { Icon(Icons.Filled.Lock, contentDescription = null) },
                                // Not armed and not Pro: the ViewModel routes
                                // this to the paywall.
                                onClick = { callbacks.onAppLockToggled(true) },
                            )
                        }
                    }
                }
                add { index, count ->
                    // Not gated on Pro: a privacy control every user must be
                    // able to reach, unlike the paid personalization above.
                    SettingsToggleRow(
                        index = index,
                        count = count,
                        label = stringResource(R.string.settings_crash_reporting),
                        supporting = stringResource(R.string.settings_crash_reporting_description),
                        leading = { Icon(Icons.Outlined.BugReport, contentDescription = null) },
                        checked = state.crashReportingEnabled,
                        onCheckedChange = callbacks.onCrashReportingToggled,
                    )
                }
            }

            // --- Data ---
            if (state.monetizationEnabled) {
                SettingsSection(title = stringResource(R.string.settings_data_section)) {
                    add { index, count ->
                        SettingsRow(
                            index = index,
                            count = count,
                            label = stringResource(R.string.settings_export_title),
                            supporting = if (isPro) {
                                stringResource(R.string.settings_export_description)
                            } else {
                                stringResource(R.string.settings_accent_locked)
                            },
                            leading = { Icon(Icons.Filled.FileDownload, contentDescription = null) },
                            trailing = {
                                if (isPro) ChevronIcon() else Icon(Icons.Filled.Lock, contentDescription = null)
                            },
                            showProgress = state.exportInFlight,
                            progressDescription = stringResource(R.string.state_working),
                            onClick = callbacks.onExportClick,
                        )
                    }
                }
            }

            // --- About ---
            SettingsSection(title = stringResource(R.string.settings_about_section)) {
                add { index, count ->
                    SettingsRow(
                        index = index,
                        count = count,
                        label = stringResource(R.string.settings_contact),
                        supporting = stringResource(R.string.settings_contact_description),
                        leading = { Icon(Icons.Filled.Mail, contentDescription = null) },
                        onClick = {
                            val emailIntent = Intent(Intent.ACTION_SENDTO).apply {
                                data = Uri.parse("mailto:")
                                putExtra(Intent.EXTRA_EMAIL, arrayOf("pawel@orzech.me"))
                                putExtra(Intent.EXTRA_SUBJECT, feedbackSubject)
                            }
                            // No resolveActivity() pre-check: on Android 11+ it is
                            // package-visibility filtered and used to report "no
                            // handler" for mailto: even with a mail app installed,
                            // silently killing the app's only support channel.
                            if (!openExternalIntent(context, emailIntent)) {
                                scope.launch { snackbarHostState.showSnackbar(noMailAppMessage) }
                            }
                        },
                    )
                }
            }

            Text(
                text = stringResource(R.string.settings_footer, state.versionName),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 28.dp),
            )
        }
    }
}

@Composable
internal fun ExportConfirmationDialog(
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Filled.FileDownload, contentDescription = null) },
        title = { Text(stringResource(R.string.settings_export_title)) },
        text = { Text(stringResource(R.string.settings_export_confirm_message)) },
        confirmButton = {
            DialogTextButton(
                text = stringResource(R.string.settings_export_title),
                onClick = onConfirm,
            )
        },
        dismissButton = {
            DialogTextButton(
                text = stringResource(R.string.email_detail_delete_cancel),
                onClick = onDismiss,
            )
        },
    )
}

@Composable
private fun DialogTextButton(
    text: String,
    onClick: () -> Unit,
    color: Color = Color.Unspecified,
) {
    TextButton(
        onClick = onClick,
        shapes = ButtonDefaults.shapes(),
        colors = if (color == Color.Unspecified) {
            ButtonDefaults.textButtonColors()
        } else {
            ButtonDefaults.textButtonColors(contentColor = color)
        },
    ) {
        Text(text)
    }
}

/** Header plus a [SegmentedGroup] of whatever rows [rows] adds, indexed as they land. */
@Composable
private fun SettingsSection(
    title: String,
    rows: MutableList<@Composable (index: Int, count: Int) -> Unit>.() -> Unit,
) {
    val list = buildList(rows)
    if (list.isEmpty()) return
    SectionHeader(title)
    SegmentedGroup(
        count = list.size,
        modifier = Modifier.padding(horizontal = 16.dp),
    ) { index ->
        list[index](index, list.size)
    }
}

/** Non-clickable group member whose children keep their own semantics. */
@Composable
private fun StaticSegment(
    index: Int,
    count: Int,
    content: @Composable RowScope.() -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(segmentedShape(index, count))
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .defaultMinSize(minHeight = 56.dp)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        content = content,
    )
}

@Composable
private fun SettingsRow(
    index: Int,
    count: Int,
    label: String,
    leading: @Composable () -> Unit,
    onClick: () -> Unit,
    supporting: String? = null,
    trailing: (@Composable () -> Unit)? = null,
    labelColor: Color = Color.Unspecified,
    // Replaces the trailing content with a loading indicator (e.g. CSV export
    // doing its network fetch) so a slow action doesn't look like a dead tap.
    showProgress: Boolean = false,
    progressDescription: String? = null,
) {
    SegmentedRow(
        index = index,
        count = count,
        modifier = if (showProgress && progressDescription != null) {
            Modifier
                .politeLiveRegion()
                .semantics { stateDescription = progressDescription }
        } else {
            Modifier
        },
        onClick = onClick,
        leading = leading,
        supporting = supporting?.let { text -> { Text(text) } },
        trailing = if (showProgress) {
            { LoadingIndicator(modifier = Modifier.size(32.dp)) }
        } else {
            trailing
        },
        headline = { Text(text = label, color = labelColor) },
    )
}

/**
 * One toggleable row with a Switch role. A clickable row plus an
 * independently-clickable Switch gives TalkBack two focus targets for a single
 * setting and announces no state on the row, so the Switch itself is purely
 * visual (`onCheckedChange = null`).
 */
@Composable
private fun SettingsToggleRow(
    index: Int,
    count: Int,
    label: String,
    supporting: String,
    leading: @Composable () -> Unit,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    val haptic = LocalHapticFeedback.current
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val spec = MaterialTheme.motionScheme.fastSpatialSpec<Dp>()
    val outer = 20.dp
    val inner = 4.dp
    val top by animateDpAsState(if (pressed || index == 0) outer else inner, spec, label = "toggleTop")
    val bottom by animateDpAsState(
        if (pressed || index == count - 1) outer else inner,
        spec,
        label = "toggleBottom",
    )
    // Spatial springs overshoot; a negative corner radius is invalid.
    val t = top.coerceAtLeast(0.dp)
    val b = bottom.coerceAtLeast(0.dp)
    val shape = RoundedCornerShape(topStart = t, topEnd = t, bottomEnd = b, bottomStart = b)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .toggleable(
                value = checked,
                interactionSource = interaction,
                indication = ripple(),
                role = Role.Switch,
                onValueChange = { enabled ->
                    haptic.performHapticFeedback(
                        if (enabled) HapticFeedbackType.ToggleOn else HapticFeedbackType.ToggleOff,
                    )
                    onCheckedChange(enabled)
                },
            )
            .defaultMinSize(minHeight = 72.dp)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        CompositionLocalProvider(LocalContentColor provides MaterialTheme.colorScheme.onSurfaceVariant) {
            leading()
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = supporting,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Switch(
            checked = checked,
            onCheckedChange = null,
            thumbContent = if (checked) {
                {
                    Icon(
                        imageVector = Icons.Filled.Check,
                        contentDescription = null,
                        modifier = Modifier.size(SwitchDefaults.IconSize),
                    )
                }
            } else {
                null
            },
        )
    }
}

/** Sunny badge with the premium glyph, the Pro row's leading element. */
@Composable
private fun ProBadge() {
    Box(
        modifier = Modifier
            .size(40.dp)
            .clip(MaterialShapes.Sunny.toShape())
            .background(MaterialTheme.colorScheme.primaryContainer),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = Icons.Filled.WorkspacePremium,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onPrimaryContainer,
            modifier = Modifier.size(22.dp),
        )
    }
}

@Composable
private fun ChevronIcon() {
    // KeyboardArrowRight is AutoMirrored, so RTL (Arabic) points "deeper".
    Icon(
        imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
        contentDescription = null,
    )
}

internal val Accent.displayNameRes: Int
    get() = when (this) {
        Accent.DYNAMIC -> R.string.accent_dynamic
        Accent.AMBER -> R.string.accent_amber
        Accent.INK -> R.string.accent_ink
        Accent.SAGE -> R.string.accent_sage
        Accent.PLUM -> R.string.accent_plum
        Accent.COBALT -> R.string.accent_cobalt
    }

private const val CLOSE_AFTER_SELECT_MS = 220L

@Composable
private fun LanguagePickerDialog(
    selected: Language?,
    onSelect: (Language?) -> Unit,
    onDismiss: () -> Unit,
) {
    // The radio animates to the chosen row first, then the dialog closes.
    var pending by remember { mutableStateOf(selected) }
    var committed by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val haptic = LocalHapticFeedback.current
    val choose: (Language?) -> Unit = { language ->
        if (!committed) {
            committed = true
            pending = language
            haptic.performHapticFeedback(HapticFeedbackType.ToggleOn)
            scope.launch {
                delay(CLOSE_AFTER_SELECT_MS)
                onSelect(language)
            }
        }
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Outlined.Language, contentDescription = null) },
        title = { Text(stringResource(R.string.settings_select_language)) },
        text = {
            // heightIn, not height: at a 200% font scale a fixed box can
            // overflow the dialog on a short screen in landscape.
            Box(modifier = Modifier.heightIn(max = 360.dp)) {
                LazyColumn(modifier = Modifier.radioButtonGroup()) {
                    item {
                        LanguageRow(
                            label = stringResource(R.string.settings_system_default),
                            isSelected = pending == null,
                            onClick = { choose(null) },
                        )
                    }
                    items(items = Language.entries.toList(), key = { it.code }) { lang ->
                        LanguageRow(
                            label = stringResource(lang.displayNameRes),
                            isSelected = pending == lang,
                            onClick = { choose(lang) },
                        )
                    }
                }
            }
        },
        confirmButton = {
            DialogTextButton(
                text = stringResource(R.string.email_detail_delete_cancel),
                onClick = onDismiss,
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
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            // TalkBack must announce which language is active, not just a list
            // of identically-labelled buttons.
            .selectable(selected = isSelected, role = Role.RadioButton, onClick = onClick)
            .heightIn(min = 48.dp)
            .padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = isSelected, onClick = null)
        Spacer(Modifier.width(12.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

// --- Previews ---------------------------------------------------------------

@Composable
private fun SettingsPreviewHost(state: SettingsViewState, accent: Accent = Accent.DYNAMIC) {
    FastMaskTheme(accent = accent) {
        SettingsContent(
            state = state,
            snackbarHostState = remember { SnackbarHostState() },
            callbacks = SettingsCallbacks(),
        )
    }
}

@Preview(name = "Free", showBackground = true, heightDp = 1500)
@Composable
private fun SettingsFreePreview() = SettingsPreviewHost(SettingsViewState())

@Preview(
    name = "Free dark",
    showBackground = true,
    heightDp = 1500,
    uiMode = android.content.res.Configuration.UI_MODE_NIGHT_YES,
)
@Composable
private fun SettingsFreeDarkPreview() = SettingsPreviewHost(SettingsViewState())

@Preview(name = "Pro, plum", showBackground = true, heightDp = 1500)
@Composable
private fun SettingsProPreview() = SettingsPreviewHost(
    SettingsViewState(isPro = true, accent = Accent.PLUM, appLockEnabled = true, selectedLanguage = Language.ENGLISH),
    accent = Accent.PLUM,
)

@Preview(
    name = "Pro dark",
    showBackground = true,
    heightDp = 1500,
    uiMode = android.content.res.Configuration.UI_MODE_NIGHT_YES,
)
@Composable
private fun SettingsProDarkPreview() = SettingsPreviewHost(
    SettingsViewState(isPro = true, accent = Accent.COBALT, exportInFlight = true),
    accent = Accent.COBALT,
)

@Preview(name = "Demo", showBackground = true, heightDp = 1500)
@Composable
private fun SettingsDemoPreview() = SettingsPreviewHost(SettingsViewState(appMode = AppMode.DEMO))
