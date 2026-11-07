package com.fastmask.ui.components.expressive

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.defaultMinSize
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
import com.fastmask.domain.model.EmailState
import com.fastmask.ui.theme.FastMaskTheme

/**
 * Small 24dp pill labelling a mask's state ("Active", "Off", "Archived",
 * "Pending"). The label is the non-colour channel; text is read by TalkBack.
 */
@Composable
fun StateChip(state: EmailState, modifier: Modifier = Modifier) {
    val pair = state.statusColors()
    Box(
        modifier = modifier
            // A floor, not a fixed height: at 200% font the label is taller than 24dp and a
            // fixed height would clip it.
            .defaultMinSize(minWidth = 24.dp, minHeight = 24.dp)
            .clip(CircleShape)
            .background(pair.container)
            .padding(horizontal = 10.dp, vertical = 2.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = stringResource(state.labelRes),
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
