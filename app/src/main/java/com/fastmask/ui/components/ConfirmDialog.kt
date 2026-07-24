package com.fastmask.ui.components

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import com.fastmask.ui.theme.FastMaskExtras

/**
 * Shared confirmation dialog matching the app's pill-button design language.
 * Used for destructive/irreversible actions (discard unsaved edits, sign out)
 * so they never happen on a single accidental tap.
 */
@Composable
fun ConfirmDialog(
    title: String,
    message: String,
    confirmText: String,
    dismissText: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    confirmVariant: PillButtonVariant = PillButtonVariant.Danger,
) {
    val extras = FastMaskExtras.current
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(20.dp),
        title = {
            Text(
                text = title,
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onSurface,
            )
        },
        text = {
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                color = extras.inkSoft,
            )
        },
        confirmButton = {
            PillButton(text = confirmText, onClick = onConfirm, variant = confirmVariant)
        },
        dismissButton = {
            PillButton(text = dismissText, onClick = onDismiss, variant = PillButtonVariant.Ghost)
        },
    )
}
