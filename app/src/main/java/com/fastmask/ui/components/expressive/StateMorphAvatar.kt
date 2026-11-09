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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.graphics.shapes.Morph
import androidx.graphics.shapes.RoundedPolygon
import com.fastmask.domain.model.EmailState
import com.fastmask.ui.theme.FastMaskTheme

internal fun polygonFor(state: EmailState): RoundedPolygon = when (state) {
    EmailState.ENABLED -> MaterialShapes.Sunny
    EmailState.PENDING -> MaterialShapes.SoftBurst
    EmailState.DISABLED -> MaterialShapes.Circle
    EmailState.DELETED -> MaterialShapes.Square
}

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
            // A plain Morph cannot restart from where the shape visually is, so a
            // mid-flight change begins from the previous target.
            from = to
            to = state
            progress.snapTo(0f)
            progress.animateTo(1f, spatial)
        }
    }

    val morph = remember(from, to) { Morph(polygonFor(from), polygonFor(to)) }
    val shape = remember(morph) { MorphShape(morph) { progress.value } }

    val pair = state.statusColors()
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
            text = maskAvatarInitial(initial),
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
