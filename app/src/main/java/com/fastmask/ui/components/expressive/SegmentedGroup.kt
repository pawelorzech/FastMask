package com.fastmask.ui.components.expressive

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.material3.Text
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.fastmask.ui.theme.FastMaskTheme

/** Gap between rows of a segmented group. */
val SegmentedGap: Dp = 2.dp

/**
 * Corner shape for row [index] of [count] in a grouped list: the first row has
 * large top corners, the last large bottom corners, a single row large corners
 * everywhere; every other corner is [inner].
 */
fun segmentedShape(
    index: Int,
    count: Int,
    outer: Dp = 20.dp,
    inner: Dp = 4.dp,
): RoundedCornerShape {
    val top = if (index == 0) outer else inner
    val bottom = if (index == count - 1) outer else inner
    return RoundedCornerShape(topStart = top, topEnd = top, bottomEnd = bottom, bottomStart = bottom)
}

/**
 * Column of [count] rows separated by [SegmentedGap]. [row] is called per index
 * and should emit a [SegmentedRow] with the same `index` and `count`.
 * For long lists use `LazyColumn` + [SegmentedRow] directly (same shapes).
 */
@Composable
fun SegmentedGroup(
    count: Int,
    modifier: Modifier = Modifier,
    row: @Composable (index: Int) -> Unit,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(SegmentedGap),
    ) {
        repeat(count) { row(it) }
    }
}

/**
 * One row of a segmented group, on `surfaceContainer`.
 *
 * Corners follow [segmentedShape]; while pressed all corners morph to [outer]
 * (`fastSpatialSpec`). Minimum height is 56dp, or 72dp when [supporting] is
 * present. When [onClick] is set the whole row is a single `Role.Button`
 * target with merged semantics; otherwise it is a static container.
 */
@Composable
fun SegmentedRow(
    index: Int,
    count: Int,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    outer: Dp = 20.dp,
    inner: Dp = 4.dp,
    leading: (@Composable () -> Unit)? = null,
    supporting: (@Composable () -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
    headline: @Composable () -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val spec = MaterialTheme.motionScheme.fastSpatialSpec<Dp>()

    val topTarget = if (pressed || index == 0) outer else inner
    val bottomTarget = if (pressed || index == count - 1) outer else inner
    val top by animateDpAsState(topTarget, spec, label = "segTop")
    val bottom by animateDpAsState(bottomTarget, spec, label = "segBottom")
    // Spatial springs overshoot; a negative corner radius is invalid.
    val t = top.coerceAtLeast(0.dp)
    val b = bottom.coerceAtLeast(0.dp)
    val shape = RoundedCornerShape(topStart = t, topEnd = t, bottomEnd = b, bottomStart = b)

    val minHeight = if (supporting != null) 72.dp else 56.dp
    val clickModifier = if (onClick != null) {
        Modifier.clickable(
            interactionSource = interaction,
            indication = androidx.compose.material3.ripple(),
            role = Role.Button,
            onClick = onClick,
        )
    } else {
        Modifier.semantics(mergeDescendants = true) { }
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .then(clickModifier)
            .defaultMinSize(minHeight = minHeight)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        if (leading != null) {
            CompositionLocalProvider(LocalContentColor provides MaterialTheme.colorScheme.onSurfaceVariant) {
                leading()
            }
        }
        Column(modifier = Modifier.weight(1f)) {
            CompositionLocalProvider(LocalContentColor provides MaterialTheme.colorScheme.onSurface) {
                ProvideTextStyle(MaterialTheme.typography.bodyLarge) { headline() }
            }
            if (supporting != null) {
                CompositionLocalProvider(LocalContentColor provides MaterialTheme.colorScheme.onSurfaceVariant) {
                    ProvideTextStyle(MaterialTheme.typography.bodyMedium) { supporting() }
                }
            }
        }
        if (trailing != null) {
            CompositionLocalProvider(LocalContentColor provides MaterialTheme.colorScheme.onSurfaceVariant) {
                trailing()
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun SegmentedGroupPreview() {
    FastMaskTheme {
        Column(Modifier.padding(16.dp)) {
            SectionHeader("Security")
            val items = listOf("App lock" to "Require biometrics", "Export" to null, "Sign out" to null)
            SegmentedGroup(count = items.size) { i ->
                SegmentedRow(
                    index = i,
                    count = items.size,
                    onClick = {},
                    supporting = items[i].second?.let { s -> { Text(s) } },
                ) { Text(items[i].first) }
            }
        }
    }
}
