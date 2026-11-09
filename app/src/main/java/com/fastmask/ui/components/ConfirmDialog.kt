package com.fastmask.ui.components

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.graphics.vector.ImageVector

@Composable
fun ConfirmDialog(
    title: String,
    message: String,
    confirmText: String,
    dismissText: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    destructive: Boolean = true,
    icon: ImageVector? = null,
) {
    val haptic = LocalHapticFeedback.current
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = icon?.let { vector -> { Icon(imageVector = vector, contentDescription = null) } },
        title = { Text(text = title) },
        text = { Text(text = message) },
        confirmButton = {
            DialogTextButton(
                text = confirmText,
                onClick = {
                    if (destructive) haptic.performHapticFeedback(HapticFeedbackType.Confirm)
                    onConfirm()
                },
                color = if (destructive) MaterialTheme.colorScheme.error else Color.Unspecified,
            )
        },
        dismissButton = {
            DialogTextButton(text = dismissText, onClick = onDismiss)
        },
    )
}
