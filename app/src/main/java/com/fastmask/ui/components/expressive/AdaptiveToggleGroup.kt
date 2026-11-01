package com.fastmask.ui.components.expressive

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity

/** Font scale from which two side-by-side toggle labels no longer fit on one line. */
private const val StackFontScale = 1.3f

/**
 * Two connected toggle buttons. Side by side normally; stacked once the user's font scale
 * makes the half-width buttons break labels mid-word ("Aktywn|a" at 200%).
 *
 * Each slot receives the [Modifier] to apply (weight or full width) and whether the group is
 * [stacked], so it can pass `vertical = stacked` to [connectedToggleShapes].
 */
@Composable
fun AdaptiveToggleGroup(
    first: @Composable (modifier: Modifier, stacked: Boolean) -> Unit,
    second: @Composable (modifier: Modifier, stacked: Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (LocalDensity.current.fontScale >= StackFontScale) {
        Column(
            modifier = modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(ConnectedGap),
        ) {
            first(Modifier.fillMaxWidth(), true)
            second(Modifier.fillMaxWidth(), true)
        }
    } else {
        Row(
            modifier = modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(ConnectedGap),
        ) {
            first(Modifier.weight(1f), false)
            second(Modifier.weight(1f), false)
        }
    }
}
