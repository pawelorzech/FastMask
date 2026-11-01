package com.fastmask.ui.components.expressive

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.fastmask.R
import com.fastmask.domain.model.EmailState
import com.fastmask.ui.theme.FastMaskTheme
import com.fastmask.ui.theme.StatusColorPair

/** Container/content pair for [state], derived from the active color scheme. */
@Composable
internal fun statusPairFor(state: EmailState): StatusColorPair = with(MaterialTheme.colorScheme) {
    when (state) {
        EmailState.ENABLED -> StatusColorPair(primaryContainer, onPrimaryContainer)
        EmailState.PENDING -> StatusColorPair(tertiaryContainer, onTertiaryContainer)
        EmailState.DISABLED -> StatusColorPair(surfaceContainerHighest, onSurfaceVariant)
        EmailState.DELETED -> StatusColorPair(errorContainer, onErrorContainer)
    }
}

internal fun stateLabelRes(state: EmailState): Int = when (state) {
    EmailState.ENABLED -> R.string.state_enabled
    EmailState.PENDING -> R.string.state_pending
    EmailState.DISABLED -> R.string.state_disabled
    EmailState.DELETED -> R.string.state_deleted
}

/**
 * Small 24dp pill labelling a mask's state ("Active", "Off", "Archived",
 * "Pending"). The label is the non-colour channel; text is read by TalkBack.
 */
@Composable
fun StateChip(state: EmailState, modifier: Modifier = Modifier) {
    val pair = statusPairFor(state)
    Box(
        modifier = modifier
            .height(24.dp)
            .defaultMinSize(minWidth = 24.dp)
            .clip(CircleShape)
            .background(pair.container)
            .padding(horizontal = 10.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = stringResource(stateLabelRes(state)),
            style = MaterialTheme.typography.labelMedium,
            color = pair.content,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun StateChipPreview() {
    FastMaskTheme {
        Column {
            EmailState.entries.forEach { StateChip(it, Modifier.padding(4.dp)) }
        }
    }
}
