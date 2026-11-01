package com.fastmask.ui.update

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import com.fastmask.R
import com.fastmask.ui.theme.FastMaskTheme

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
    val haptic = LocalHapticFeedback.current
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(imageVector = Icons.Filled.SystemUpdate, contentDescription = null) },
        title = { Text(stringResource(R.string.update_ready_title)) },
        text = { Text(stringResource(R.string.update_ready_message)) },
        confirmButton = {
            TextButton(
                onClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.Confirm)
                    onInstall()
                },
                shapes = ButtonDefaults.shapes(),
            ) {
                Text(stringResource(R.string.update_ready_install))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, shapes = ButtonDefaults.shapes()) {
                Text(stringResource(R.string.update_ready_later))
            }
        },
    )
}

@Preview(name = "Update dialog", showBackground = true)
@Composable
private fun UpdateReadyDialogPreview() {
    FastMaskTheme { UpdateReadyDialog(onInstall = {}, onDismiss = {}) }
}
