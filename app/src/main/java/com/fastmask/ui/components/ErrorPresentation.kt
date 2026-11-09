package com.fastmask.ui.components

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.fastmask.R
import com.fastmask.ui.accessibility.politeLiveRegion
import com.fastmask.ui.common.UiError
import com.fastmask.ui.common.UiErrorAction

/*
 * The one error presentation: an inline banner and a full-screen state, both
 * driven by a UiError. What the recovery button says and does comes from
 * UiError.recovery below and nowhere else, so a screen cannot offer "Retry" for a
 * refused token by forgetting a case.
 */

private class Recovery(@StringRes val label: Int, val onClick: () -> Unit)

/**
 * @param onRetry the screen's own re-run of what failed, or null when it has
 *   none (an in-form failure the user fixes by editing); then only a
 *   [UiErrorAction.REAUTHENTICATE] error gets a button.
 */
private fun UiError.recovery(onRetry: (() -> Unit)?, onReauthenticate: () -> Unit): Recovery? =
    when (action) {
        UiErrorAction.REAUTHENTICATE -> Recovery(R.string.settings_signin_with_fastmail, onReauthenticate)
        UiErrorAction.RETRY, UiErrorAction.NONE -> onRetry?.let { Recovery(R.string.error_retry, it) }
    }

@Composable
internal fun InlineErrorBanner(
    error: UiError,
    onRetry: (() -> Unit)?,
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

/**
 * Full-screen failure. [heroShape] and [icon] are each screen's identity;
 * everything else is shared.
 */
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
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(32.dp)
            .politeLiveRegion(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        StateIllustration(
            icon = icon,
            shape = heroShape,
            container = MaterialTheme.colorScheme.errorContainer,
            content = MaterialTheme.colorScheme.onErrorContainer,
        )
        Spacer(Modifier.size(24.dp))
        Text(
            text = stringResource(title),
            style = MaterialTheme.typography.headlineSmallEmphasized,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.size(8.dp))
        Text(
            text = stringResource(error.message),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        if (recovery != null) {
            Spacer(Modifier.size(24.dp))
            FilledTonalButton(onClick = recovery.onClick) {
                Text(stringResource(recovery.label))
            }
        }
    }
}
