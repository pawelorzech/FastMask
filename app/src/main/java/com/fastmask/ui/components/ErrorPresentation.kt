package com.fastmask.ui.components

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.fastmask.R
import com.fastmask.ui.accessibility.politeLiveRegion
import com.fastmask.ui.common.UiError
import com.fastmask.ui.common.UiErrorAction


private class Recovery(@StringRes val label: Int, val onClick: () -> Unit)

private fun UiError.recovery(onRetry: () -> Unit, onReauthenticate: () -> Unit): Recovery? =
    when (action) {
        UiErrorAction.REAUTHENTICATE -> Recovery(R.string.settings_signin_with_fastmail, onReauthenticate)
        UiErrorAction.RETRY -> Recovery(R.string.error_retry, onRetry)
        UiErrorAction.NONE -> null
    }

@Composable
internal fun InlineErrorBanner(
    error: UiError,
    onRetry: () -> Unit,
    onReauthenticate: () -> Unit,
    modifier: Modifier = Modifier,
    actionEnabled: Boolean = true,
) {
    val recovery = error.recovery(onRetry, onReauthenticate)
    Surface(
        modifier = modifier
            .fillMaxWidth()
            // Keep the recovery button a separate semantics node: merging
            // descendants would hide it from TalkBack.
            .politeLiveRegion(),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.errorContainer,
        contentColor = MaterialTheme.colorScheme.onErrorContainer,
    ) {
        Row(
            modifier = Modifier.padding(start = 16.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Icon(
                imageVector = Icons.Filled.ErrorOutline,
                contentDescription = null,
                modifier = Modifier.size(20.dp),
            )
            Text(
                text = stringResource(error.message),
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.weight(1f),
            )
            if (recovery != null) {
                TextButton(
                    onClick = recovery.onClick,
                    enabled = actionEnabled,
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = MaterialTheme.colorScheme.onErrorContainer,
                    ),
                ) {
                    Text(stringResource(recovery.label), style = MaterialTheme.typography.labelLargeEmphasized)
                }
            }
        }
    }
}

@Composable
internal fun ErrorState(
    @StringRes title: Int,
    error: UiError,
    onRetry: () -> Unit,
    onReauthenticate: () -> Unit,
    modifier: Modifier = Modifier,
    heroShape: Shape = MaterialShapes.Cookie9Sided.toShape(),
    icon: ImageVector = Icons.Filled.CloudOff,
) {
    val recovery = error.recovery(onRetry, onReauthenticate)
    StateBlock(
        icon = icon,
        title = stringResource(title),
        body = stringResource(error.message),
        contentPadding = PaddingValues(32.dp),
        modifier = modifier,
        liveRegion = true,
        illustrationShape = heroShape,
        illustrationContainer = MaterialTheme.colorScheme.errorContainer,
        illustrationContent = MaterialTheme.colorScheme.onErrorContainer,
        action = recovery?.let {
            {
                FilledTonalButton(onClick = it.onClick) {
                    Text(stringResource(it.label))
                }
            }
        },
    )
}
