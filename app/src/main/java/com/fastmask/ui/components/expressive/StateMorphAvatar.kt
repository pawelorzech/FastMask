package com.fastmask.ui.components.expressive

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.toPath
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Matrix
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.graphics.shapes.Morph
import androidx.graphics.shapes.RoundedPolygon
import com.fastmask.domain.model.EmailState
import com.fastmask.ui.theme.FastMaskTheme

/** Shape for a state: ENABLED Sunny, PENDING SoftBurst, DISABLED Circle, DELETED Square. */
internal fun polygonFor(state: EmailState): RoundedPolygon = when (state) {
    EmailState.ENABLED -> MaterialShapes.Sunny
    EmailState.PENDING -> MaterialShapes.SoftBurst
    EmailState.DISABLED -> MaterialShapes.Circle
    EmailState.DELETED -> MaterialShapes.Square
}

/** Clips to the [morph] at [progress] (read lazily, so animation does not recompose). */
private class MorphShape(
    private val morph: Morph,
    private val progress: () -> Float,
) : Shape {
    private val path = Path()
    private val matrix = Matrix()

    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
        path.rewind()
        morph.toPath(progress = progress(), path = path)
        // MaterialShapes polygons are normalized to a 1x1 box.
        matrix.reset()
        matrix.scale(size.width, size.height)
        path.transform(matrix)
        return Outline.Generic(path)
    }
}

/**
 * Avatar whose shape encodes the mask's [state] (Sunny / SoftBurst / Circle /
 * Square) and morphs between shapes when the state changes, using
 * `fastSpatialSpec()`; colors cross-fade with `defaultEffectsSpec()`.
 * Decorative (no semantics); pair with a [StateChip].
 */
@Composable
fun StateMorphAvatar(
    initial: String,
    state: EmailState,
    modifier: Modifier = Modifier,
    size: Dp = 40.dp,
) {
    var from by remember { mutableStateOf(state) }
    var to by remember { mutableStateOf(state) }
    val progress = remember { Animatable(1f) }
    val spatial = MaterialTheme.motionScheme.fastSpatialSpec<Float>()

    LaunchedEffect(state) {
        if (state != to) {
            // Restart from wherever the shape visually is is not possible with a
            // plain Morph, so a mid-flight change begins from the previous target.
            from = to
            to = state
            progress.snapTo(0f)
            progress.animateTo(1f, spatial)
        }
    }

    val morph = remember(from, to) { Morph(polygonFor(from), polygonFor(to)) }
    val shape = remember(morph) { MorphShape(morph) { progress.value } }

    val pair = statusPairFor(state)
    val effects = MaterialTheme.motionScheme.defaultEffectsSpec<androidx.compose.ui.graphics.Color>()
    val container by animateColorAsState(pair.container, effects, label = "avatarContainer")
    val content by animateColorAsState(pair.content, effects, label = "avatarContent")

    Box(
        modifier = modifier
            .size(size)
            .clip(shape)
            .background(container)
            .clearAndSetSemantics { },
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = initial.trim().take(1).uppercase().ifEmpty { "?" },
            color = content,
            style = MaterialTheme.typography.titleMediumEmphasized,
            fontSize = (size.value * 0.4f).sp,
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun StateMorphAvatarPreview() {
    FastMaskTheme {
        Row(Modifier.padding(8.dp)) {
            EmailState.entries.forEach { StateMorphAvatar("A", it, Modifier.padding(4.dp)) }
        }
    }
}
