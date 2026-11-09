package com.fastmask.ui.list

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.ToggleButton
import androidx.compose.material3.ToggleButtonDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.Measurable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.layout.SubcomposeLayout
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import com.fastmask.R
import com.fastmask.ui.components.expressive.ConnectedGap
import com.fastmask.ui.components.expressive.connectedToggleShapes

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun FilterGroup(
    selected: EmailFilter,
    counts: Map<EmailFilter, Int>,
    onSelect: (EmailFilter) -> Unit,
    modifier: Modifier = Modifier,
) {
    val filters = EmailFilter.entries
    val scroll = rememberScrollState()
    val density = LocalDensity.current
    val gutterPx = with(density) { ListGutter.roundToPx() }
    val gapPx = with(density) { ConnectedGap.roundToPx() }
    val scrollSpec = MaterialTheme.motionScheme.defaultSpatialSpec<Float>()
    val spans = remember { mutableStateMapOf<EmailFilter, IntRange>() }

    BoxWithConstraints(modifier = modifier.fillMaxWidth().selectableGroup()) {
        val viewportPx = constraints.maxWidth
        val span = spans[selected]

        LaunchedEffect(selected, span, viewportPx) {
            span ?: return@LaunchedEffect
            val start = scroll.value
            val target = when {
                span.first - gutterPx < start -> span.first - gutterPx
                span.last + gutterPx > start + viewportPx -> span.last + gutterPx - viewportPx
                else -> return@LaunchedEffect
            }
            scroll.animateScrollTo(target.coerceAtLeast(0), scrollSpec)
        }

        FilterRowLayout(
            viewportPx = viewportPx,
            gutterPx = gutterPx,
            gapPx = gapPx,
            modifier = Modifier.horizontalScroll(scroll),
        ) { showCounts ->
            filters.forEachIndexed { index, filter ->
                val label = stringResource(filter.labelRes)
                ToggleButton(
                    checked = selected == filter,
                    onCheckedChange = { checked -> if (checked) onSelect(filter) },
                    shapes = connectedToggleShapes(index = index, count = filters.size),
                    // Unchecked buttons sit on the header, which turns surfaceContainer once
                    // the app bar collapses; the highest container tone keeps them visible on both.
                    colors = ToggleButtonDefaults.colors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                    ),
                    // Single-select: announce as a radio, not a checkbox.
                    modifier = Modifier
                        .semantics { role = Role.RadioButton }
                        .onPlaced { coords ->
                            val x = coords.positionInParent().x.toInt()
                            spans[filter] = x..(x + coords.size.width)
                        },
                ) {
                    Text(text = label, style = MaterialTheme.typography.labelLarge)
                    if (showCounts) {
                        Spacer(Modifier.width(6.dp))
                        Text(
                            text = (counts[filter] ?: 0).toString(),
                            style = MaterialTheme.typography.labelMedium,
                        )
                    }
                }
            }
        }
    }
}

/** Counts are dropped when the row would overflow: the app bar subtitle already states them. */
@Composable
private fun FilterRowLayout(
    viewportPx: Int,
    gutterPx: Int,
    gapPx: Int,
    modifier: Modifier = Modifier,
    content: @Composable (showCounts: Boolean) -> Unit,
) {
    SubcomposeLayout(modifier = modifier) { _ ->
        val available = viewportPx - 2 * gutterPx
        fun natural(ms: List<Measurable>) = ms.map { it.maxIntrinsicWidth(Constraints.Infinity) }
        fun total(ws: List<Int>) = ws.sum() + gapPx * (ws.size - 1)

        // Probe: the with-counts row, measured for its width only. Its semantics are
        // cleared, so neither TalkBack nor tests ever see a second set of buttons
        // (it is never placed, so its onPlaced callbacks never fire either).
        val probe = subcompose("probe") {
            Row(
                modifier = Modifier.clearAndSetSemantics { },
                horizontalArrangement = Arrangement.spacedBy(gapPx.toDp()),
            ) { content(true) }
        }.first()
        val showCounts = probe.maxIntrinsicWidth(Constraints.Infinity) <= available
        val measurables = subcompose(showCounts) { content(showCounts) }
        val naturalWidths = natural(measurables)
        val count = measurables.size
        val sum = total(naturalWidths)
        val fits = sum <= available
        val extra = if (fits) available - sum else 0
        val height = measurables.indices.maxOf { measurables[it].maxIntrinsicHeight(naturalWidths[it]) }

        val placeables = measurables.mapIndexed { i, measurable ->
            val share = extra / count + if (i == count - 1) extra % count else 0
            measurable.measure(Constraints.fixed(naturalWidths[i] + share, height))
        }
        val width = if (fits) viewportPx else sum + 2 * gutterPx
        layout(width, height) {
            var x = gutterPx
            placeables.forEach {
                it.placeRelative(x, 0)
                x += it.width + gapPx
            }
        }
    }
}
