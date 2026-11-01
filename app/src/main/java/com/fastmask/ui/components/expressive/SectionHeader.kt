package com.fastmask.ui.components.expressive

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.fastmask.ui.theme.FastMaskTheme

/** Header above a [SegmentedGroup]: titleSmall in `primary`, inset 32dp from the start. */
@Composable
fun SectionHeader(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = modifier
            .padding(start = 32.dp, end = 16.dp, top = 16.dp, bottom = 8.dp)
            .semantics { heading() },
    )
}

@Preview(showBackground = true)
@Composable
private fun SectionHeaderPreview() {
    FastMaskTheme { SectionHeader("Appearance") }
}
