package com.fastmask.ui.update

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.fastmask.R
import com.fastmask.ui.components.ConfirmDialog
import com.fastmask.ui.components.PillButtonVariant

/**
 * The restart prompt for a flexible update whose download has finished.
 *
 * A modal is defensible here and only here. Everything up to this point happened in Play's own UI
 * after the user tapped through an offer, so this is the end of something they started rather than
 * an interruption out of nowhere — and Play will install the update on its own next time the app
 * stops, so "Later" costs nothing.
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
    )
}
