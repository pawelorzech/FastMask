package com.fastmask.ui.settings

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.Description
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
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import com.fastmask.ui.accessibility.screenHeading
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.fastmask.BuildConfig
import com.fastmask.R
import com.fastmask.ui.common.ContentWidth
import com.fastmask.ui.common.readableWidth
import com.fastmask.domain.model.Accent
import com.fastmask.domain.model.Language
import com.fastmask.core.platform.openExternalIntent
import com.fastmask.ui.components.ConfirmDialog
import com.fastmask.ui.components.DialogTextButton
import com.fastmask.ui.lock.canUseAppLock
import com.fastmask.ui.theme.FastMaskTheme
import com.fastmask.ui.theme.style
import kotlinx.coroutines.launch

/** Everything the settings content draws, so it can be previewed without Hilt. */
internal data class SettingsViewState(
    val isDemo: Boolean = false,
    val isPro: Boolean = false,
    val accent: Accent = Accent.DYNAMIC,
    val appLockEnabled: Boolean = false,
    val crashReportingEnabled: Boolean = true,
    val selectedLanguage: Language? = null,
    val exportInFlight: Boolean = false,
    val showExportConfirmation: Boolean = false,
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
    val onExportConfirmed: () -> Unit = {},
    val onExportDismissed: () -> Unit = {},
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
    val session by viewModel.session.collectAsState()
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
                    runCatching {
                        val send = viewModel.shareIntent(event.file)
                        context.startActivity(Intent.createChooser(send, exportChooserTitle))
                    }.onFailure {
                        snackbarHostState.showSnackbar(exportWriteFailedMessage)
                    }
                }
            }
        }
    }

    SettingsContent(
        state = SettingsViewState(
            isDemo = session.isDemo,
            isPro = proStatus.isPro,
            accent = selectedAccent.shownFor(proStatus),
            appLockEnabled = appLockEnabled,
            crashReportingEnabled = crashReportingEnabled,
            selectedLanguage = uiState.selectedLanguage,
            exportInFlight = uiState.exportInFlight,
            showExportConfirmation = uiState.showExportConfirmation,
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
            onUnlockColors = viewModel::onAccentClick,
            onAppLockToggled = viewModel::onAppLockToggled,
            onCrashReportingToggled = viewModel::onCrashReportingToggled,
            onExportClick = viewModel::onExportClick,
            onExportConfirmed = viewModel::onExportConfirmed,
            onExportDismissed = viewModel::onExportConfirmationDismissed,
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
    val scope = rememberCoroutineScope()
    var showLanguageDialog by remember { mutableStateOf(false) }
    var showLockUnavailableDialog by remember { mutableStateOf(false) }
    var showLogoutDialog by remember { mutableStateOf(false) }
    var showLicensesDialog by rememberSaveable { mutableStateOf(false) }

    val colorsLockedMessage = stringResource(R.string.settings_accent_locked)
    val unlockLabel = stringResource(R.string.settings_colors_unlock)
    val noMailAppMessage = stringResource(R.string.error_no_app_for_link)
    val feedbackSubject = stringResource(R.string.settings_feedback_subject)
    val supportEmail = stringResource(R.string.support_email)
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

    if (state.showExportConfirmation) {
        ConfirmDialog(
            title = stringResource(R.string.settings_export_title),
            message = stringResource(R.string.settings_export_confirm_message),
            confirmText = stringResource(R.string.settings_export_title),
            dismissText = stringResource(R.string.email_detail_delete_cancel),
            onConfirm = callbacks.onExportConfirmed,
            onDismiss = callbacks.onExportDismissed,
            destructive = false,
            icon = Icons.Filled.FileDownload,
        )
    }

    if (showLicensesDialog) {
        LicensesDialog(onDismiss = { showLicensesDialog = false })
    }

    if (showLogoutDialog) {
        ConfirmDialog(
            title = stringResource(R.string.logout_confirm_title),
            message = stringResource(R.string.logout_confirm_message),
            confirmText = stringResource(R.string.settings_logout),
            dismissText = stringResource(R.string.email_detail_delete_cancel),
            onConfirm = {
                showLogoutDialog = false
                callbacks.onSignOut()
            },
            onDismiss = { showLogoutDialog = false },
            icon = Icons.AutoMirrored.Filled.Logout,
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
                        modifier = Modifier.screenHeading(),
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
                .readableWidth(ContentWidth.Form)
                .padding(bottom = 32.dp),
        ) {
            // --- Account ---
            SettingsSection(title = stringResource(R.string.settings_demo_section)) {
                if (state.isDemo) {
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
                                Text(
                                    text = stringResource(state.accent.style.nameRes),
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
                        // An armed lock keeps its switch even without Pro so it can
                        // always be turned off; the "Pro" placeholder is only for an
                        // unarmed lock.
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
                                onClick = { callbacks.onAppLockToggled(true) },
                            )
                        }
                    }
                }
                add { index, count ->
                    // Not gated on Pro: a privacy control every user must reach.
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
                        label = stringResource(R.string.settings_licenses_title),
                        supporting = stringResource(R.string.settings_licenses_description),
                        leading = { Icon(Icons.Filled.Description, contentDescription = null) },
                        trailing = { ChevronIcon() },
                        onClick = { showLicensesDialog = true },
                    )
                }
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
                                putExtra(Intent.EXTRA_EMAIL, arrayOf(supportEmail))
                                putExtra(Intent.EXTRA_SUBJECT, feedbackSubject)
                            }
                            // No resolveActivity() pre-check: on Android 11+ it is
                            // package-visibility filtered and can report no handler for
                            // mailto: even with a mail app installed.
                            if (!openExternalIntent(context, emailIntent)) {
                                scope.launch { snackbarHostState.showSnackbar(noMailAppMessage) }
                            }
                        },
                    )
                }
            }

            // --- More from the maker ---
            SettingsSection(title = stringResource(R.string.more_apps_title)) {
                MoreApps.otherThan(context.packageName).forEach { app ->
                    add { index, count ->
                        SettingsRow(
                            index = index,
                            count = count,
                            label = app.name,
                            supporting = stringResource(app.taglineRes),
                            leading = { Icon(Icons.Filled.Apps, contentDescription = null) },
                            trailing = { ChevronIcon() },
                            onClick = { MoreApps.open(context, app.packageId) },
                        )
                    }
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
private fun SettingsDemoPreview() = SettingsPreviewHost(SettingsViewState(isDemo = true))
