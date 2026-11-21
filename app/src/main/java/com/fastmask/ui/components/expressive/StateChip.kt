package com.fastmask.ui.components.expressive

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.fastmask.domain.model.EmailState
import com.fastmask.ui.theme.FastMaskTheme

/** The label is the non-colour channel for the state. */
@Composable
fun StateChip(state: EmailState, modifier: Modifier = Modifier) {
    val motion = MaterialTheme.motionScheme
    val pair = state.statusColors()
    val containerColor by animateColorAsState(
        targetValue = pair.container,
        animationSpec = motion.defaultEffectsSpec(),
        label = "stateChipContainer",
    )
    val contentColor by animateColorAsState(
        targetValue = pair.content,
        animationSpec = motion.defaultEffectsSpec(),
        label = "stateChipContent",
    )
    val enterOffset = motion.fastSpatialSpec<IntOffset>()
    val exitOffset = motion.fastSpatialSpec<IntOffset>()
    val fadeInSpec = motion.fastEffectsSpec<Float>()
    val fadeOutSpec = motion.fastEffectsSpec<Float>()
    val sizeSpec = motion.defaultSpatialSpec<IntSize>()

    Box(
        modifier = modifier
            // A floor, not a fixed height: at 200% font the label is taller than 24dp and a
            // fixed height would clip it.
            .defaultMinSize(minWidth = 24.dp, minHeight = 24.dp)
            .clip(CircleShape)
            .background(containerColor)
            .padding(horizontal = 10.dp, vertical = 2.dp),
        contentAlignment = Alignment.Center,
    ) {
        AnimatedContent(
            targetState = state,
            transitionSpec = {
                (slideInVertically(enterOffset) { it / 2 } + fadeIn(fadeInSpec))
                    .togetherWith(slideOutVertically(exitOffset) { -it / 2 } + fadeOut(fadeOutSpec))
                    .using(SizeTransform(clip = false) { _, _ -> sizeSpec })
            },
            label = "stateChipLabel",
        ) { current ->
            Text(
                text = stringResource(current.labelRes),
                style = MaterialTheme.typography.labelMedium,
                color = contentColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
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
