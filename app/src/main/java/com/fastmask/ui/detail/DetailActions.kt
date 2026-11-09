package com.fastmask.ui.detail

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ButtonGroup
import androidx.compose.material3.ButtonGroupDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.fastmask.R
import com.fastmask.ui.accessibility.politeLiveRegion
import com.fastmask.ui.components.ButtonLoadingLabel

@Composable
internal fun CopyButton(copied: Boolean, onClick: () -> Unit) {
    val motion = rememberMotion()
    val height = ButtonDefaults.MediumContainerHeight
    Button(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = height),
        shapes = ButtonDefaults.shapes(),
        contentPadding = ButtonDefaults.contentPaddingFor(height),
    ) {
        AnimatedContent(
            targetState = copied,
            transitionSpec = {
                (fadeIn(motion.fastEffects) +
                    scaleIn(motion.fastSpatial, initialScale = 0.85f)) togetherWith
                    fadeOut(motion.fastEffects)
            },
            label = "copyLabel",
        ) { isCopied ->
            Row(
                horizontalArrangement = Arrangement.spacedBy(ButtonDefaults.iconSpacingFor(height)),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = if (isCopied) Icons.Filled.Check else Icons.Filled.ContentCopy,
                    contentDescription = null,
                    modifier = Modifier.size(ButtonDefaults.iconSizeFor(height)),
                )
                Text(
                    text = stringResource(
                        if (isCopied) R.string.email_detail_copied else R.string.email_detail_copy_email,
                    ),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.politeLiveRegion(),
                )
            }
        }
    }
}

@Composable
internal fun ArchiveButton(
    deleting: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val height = ButtonDefaults.MediumContainerHeight
    val error = MaterialTheme.colorScheme.error
    val archiveDesc = stringResource(R.string.email_detail_delete)
    OutlinedButton(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = height)
            // The label is also the accessible name; spelled out so the button
            // stays addressable by name (TalkBack actions, instrumented tests).
            .semantics { contentDescription = archiveDesc },
        shapes = ButtonDefaults.shapes(),
        colors = ButtonDefaults.outlinedButtonColors(contentColor = error),
        border = BorderStroke(1.dp, error.copy(alpha = if (enabled) 1f else 0.38f)),
        contentPadding = ButtonDefaults.contentPaddingFor(height),
    ) {
        ButtonLoadingLabel(
            loading = deleting,
            loadingDescription = stringResource(R.string.state_working),
            label = stringResource(R.string.email_detail_delete),
            icon = Icons.Filled.Archive,
            iconSize = ButtonDefaults.iconSizeFor(height),
            iconSpacing = ButtonDefaults.iconSpacingFor(height),
            textStyle = MaterialTheme.typography.titleMedium,
        )
    }
}

@Composable
internal fun EditActions(
    saving: Boolean,
    busy: Boolean,
    canSave: Boolean,
    onCancel: () -> Unit,
    onSave: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val height = ButtonDefaults.MediumContainerHeight
    val cancelText = stringResource(R.string.action_cancel)
    val saveText = stringResource(R.string.email_detail_save)
    val workingDesc = stringResource(R.string.state_working)
    ButtonGroup(
        overflowIndicator = { menuState -> ButtonGroupDefaults.OverflowIndicator(menuState) },
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
    ) {
        customItem(
            buttonGroupContent = {
                val source = remember { MutableInteractionSource() }
                OutlinedButton(
                    onClick = onCancel,
                    enabled = !busy,
                    interactionSource = source,
                    modifier = Modifier
                        .heightIn(min = height)
                        .animateWidth(source),
                    shapes = ButtonDefaults.shapes(),
                    contentPadding = ButtonDefaults.contentPaddingFor(height),
                ) { Text(text = cancelText, style = MaterialTheme.typography.titleMedium) }
            },
            menuContent = { state ->
                DropdownMenuItem(
                    text = { Text(cancelText) },
                    onClick = { onCancel(); state.dismiss() },
                )
            },
        )
        customItem(
            buttonGroupContent = {
                val source = remember { MutableInteractionSource() }
                Button(
                    onClick = onSave,
                    enabled = canSave && !busy,
                    interactionSource = source,
                    modifier = Modifier
                        .heightIn(min = height)
                        .animateWidth(source),
                    shapes = ButtonDefaults.shapes(),
                    contentPadding = ButtonDefaults.contentPaddingFor(height),
                ) {
                    ButtonLoadingLabel(loading = saving, loadingDescription = workingDesc) {
                        Text(text = saveText, style = MaterialTheme.typography.titleMedium)
                    }
                }
            },
            menuContent = { state ->
                DropdownMenuItem(
                    text = { Text(saveText) },
                    enabled = canSave && !busy,
                    onClick = { onSave(); state.dismiss() },
                )
            },
        )
    }
}
