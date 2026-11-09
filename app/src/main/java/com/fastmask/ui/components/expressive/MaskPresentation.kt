package com.fastmask.ui.components.expressive

import androidx.annotation.StringRes
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import com.fastmask.R
import com.fastmask.domain.model.EmailState
import com.fastmask.ui.theme.StatusColorPair

@get:StringRes
internal val EmailState.labelRes: Int
    get() = when (this) {
        EmailState.ENABLED -> R.string.state_enabled
        EmailState.PENDING -> R.string.state_pending
        EmailState.DISABLED -> R.string.state_disabled
        EmailState.DELETED -> R.string.state_deleted
    }

@Composable
internal fun EmailState.statusColors(): StatusColorPair = with(MaterialTheme.colorScheme) {
    when (this@statusColors) {
        EmailState.ENABLED -> StatusColorPair(primaryContainer, onPrimaryContainer)
        EmailState.PENDING -> StatusColorPair(tertiaryContainer, onTertiaryContainer)
        EmailState.DISABLED -> StatusColorPair(surfaceContainerHighest, onSurfaceVariant)
        EmailState.DELETED -> StatusColorPair(errorContainer, onErrorContainer)
    }
}
