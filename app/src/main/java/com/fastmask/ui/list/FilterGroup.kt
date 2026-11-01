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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.Layout
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

/** Side gutter of the list screen; the filter row scrolls through it to the screen edge. */
internal val FilterGutter = 16.dp

/**
 * Single-select filter: a connected group of toggle buttons. The selected one is
 * filled and fully rounded (stock `ToggleButton` shape morph); the rest keep the
 * flat inner corners of a connected group.
 *
 * The row is meant to be placed full-bleed (no parent horizontal padding) and
 * supplies the [FilterGutter] itself as content padding:
 *  - if the natural button widths fit between the gutters they share the leftover
 *    width equally, no scrolling;
 *  - otherwise the row scrolls edge to edge, so an overflowing button slides out
 *    under the screen edge (the standard chip-row peek) rather than being clipped
 *    at the gutter;
 *  - when [selected] changes it is scrolled fully into view, gutter included.
 */
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
    val gutterPx = with(density) { FilterGutter.roundToPx() }
    val gapPx = with(density) { ConnectedGap.roundToPx() }
    val scrollSpec = MaterialTheme.motionScheme.defaultSpatialSpec<Float>()
    // Horizontal extent (start, end) of each button inside the scrolled content.
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
        ) {
            filters.forEachIndexed { index, filter ->
                val label = stringResource(
                    when (filter) {
                        EmailFilter.ALL -> R.string.filter_all
                        EmailFilter.ENABLED -> R.string.filter_enabled
                        EmailFilter.DISABLED -> R.string.filter_disabled
                        EmailFilter.DELETED -> R.string.filter_deleted
                    },
                )
                ToggleButton(
                    checked = selected == filter,
                    onCheckedChange = { checked -> if (checked) onSelect(filter) },
                    shapes = connectedToggleShapes(index = index, count = filters.size),
                    // Single-select: announce as a radio, not a checkbox.
                    modifier = Modifier
                        .semantics { role = Role.RadioButton }
                        .onPlaced { coords ->
                            val x = coords.positionInParent().x.toInt()
                            spans[filter] = x..(x + coords.size.width)
                        },
                ) {
                    Text(text = label, style = MaterialTheme.typography.labelLarge)
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

/**
 * Lays the buttons out in one row with [gutterPx] of padding at both ends.
 * Buttons are first sized at their natural (intrinsic) width. If that total fits the
 * viewport minus the gutters, the leftover is shared equally and the content is
 * exactly viewport wide (nothing to scroll). If not, buttons keep their natural
 * widths and the content grows past the viewport so the parent scroll takes over.
 */
@Composable
private fun FilterRowLayout(
    viewportPx: Int,
    gutterPx: Int,
    gapPx: Int,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Layout(content = content, modifier = modifier) { measurables, _ ->
        val count = measurables.size
        val naturalWidths = measurables.map { it.maxIntrinsicWidth(Constraints.Infinity) }
        val gaps = gapPx * (count - 1)
        val available = viewportPx - 2 * gutterPx
        val total = naturalWidths.sum() + gaps
        val fits = total <= available
        val extra = if (fits) available - total else 0
        val height = measurables.indices.maxOf { measurables[it].maxIntrinsicHeight(naturalWidths[it]) }

        val placeables = measurables.mapIndexed { i, measurable ->
            val share = extra / count + if (i == count - 1) extra % count else 0
            val width = naturalWidths[i] + share
            measurable.measure(Constraints.fixed(width, height))
        }
        val width = if (fits) viewportPx else total + 2 * gutterPx
        layout(width, height) {
            var x = gutterPx
            placeables.forEach {
                it.placeRelative(x, 0)
                x += it.width + gapPx
            }
        }
    }
}
