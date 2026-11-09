package com.fastmask.ui.update

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import com.fastmask.R
import com.fastmask.ui.components.ConfirmDialog
import com.fastmask.ui.theme.FastMaskTheme

/**
 * A modal is defensible here and only here: everything up to this point happened in Play's
 * own UI after the user tapped through an offer, so this is the end of something they
 * started, not an interruption out of nowhere. Play installs the update on its own next
 * time the app stops, so "Later" costs nothing.
 */
@Composable
fun UpdateReadyDialog(
    onInstall: () -> Unit,
    onDismiss: () -> Unit,
) {
    ConfirmDialog(
        title = stringResource(R.string.update_ready_title),
        message = stringResource(R.string.update_ready_message),
        confirmText = stringResource(R.string.update_ready_install),
        dismissText = stringResource(R.string.update_ready_later),
        onConfirm = onInstall,
        onDismiss = onDismiss,
        destructive = false,
        confirmHaptic = true,
        icon = Icons.Filled.SystemUpdate,
    )
}

@Preview(name = "Update dialog", showBackground = true)
@Composable
private fun UpdateReadyDialogPreview() {
    FastMaskTheme { UpdateReadyDialog(onInstall = {}, onDismiss = {}) }
}
