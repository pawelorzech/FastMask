package com.fastmask.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.fastmask.R
import com.fastmask.ui.accessibility.politeLiveRegion
import com.fastmask.ui.theme.FastMaskTheme

/**
 * Full-size error state: a SoftBurst shape in errorContainer holding an icon,
 * the message, and (when [onRetry] is given) one clear retry action.
 */
@Composable
fun ErrorMessage(
    message: String,
    modifier: Modifier = Modifier,
    onRetry: (() -> Unit)? = null,
) {
    val haptic = LocalHapticFeedback.current
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box(
            modifier = Modifier
                .size(112.dp)
                .clip(MaterialShapes.SoftBurst.toShape())
                .background(MaterialTheme.colorScheme.errorContainer)
                .clearAndSetSemantics { },
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Filled.ErrorOutline,
                contentDescription = null,
                modifier = Modifier.size(48.dp),
                tint = MaterialTheme.colorScheme.onErrorContainer,
            )
        }
        Spacer(modifier = Modifier.height(24.dp))
        Text(
            text = message,
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.politeLiveRegion(),
        )
        if (onRetry != null) {
            Spacer(modifier = Modifier.height(24.dp))
            val buttonHeight = ButtonDefaults.MediumContainerHeight
            FilledTonalButton(
                onClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.Confirm)
                    onRetry()
                },
                shapes = ButtonDefaults.shapesFor(buttonHeight),
                contentPadding = ButtonDefaults.contentPaddingFor(buttonHeight, hasStartIcon = true),
                modifier = Modifier.height(buttonHeight),
            ) {
                Icon(
                    imageVector = Icons.Filled.Refresh,
                    contentDescription = null,
                    modifier = Modifier.size(ButtonDefaults.iconSizeFor(buttonHeight)),
                )
                Spacer(Modifier.size(ButtonDefaults.iconSpacingFor(buttonHeight)))
                Text(stringResource(R.string.error_retry))
            }
        }
    }
}

@Preview(name = "Error light", showBackground = true, widthDp = 360, heightDp = 480)
@Composable
private fun ErrorMessageLightPreview() {
    FastMaskTheme(darkTheme = false) {
        ErrorMessage(message = "Couldn't reach Fastmail. Check your connection.", onRetry = {})
    }
}

@Preview(name = "Error dark no retry", showBackground = true, widthDp = 360, heightDp = 480)
@Composable
private fun ErrorMessageDarkPreview() {
    FastMaskTheme(darkTheme = true) {
        ErrorMessage(message = "Couldn't reach Fastmail. Check your connection.")
    }
}
