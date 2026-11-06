package com.fastmask.ui.pro

import androidx.compose.animation.core.animate
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.WorkspacePremium
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.fastmask.ui.theme.FastMaskTheme
import com.fastmask.ui.components.expressive.MorphingShape

/**
 * The Pro paywall hero: three [MaterialShapes] on staggered morph timers, the
 * main one carrying the premium glyph. When [celebrate] flips on (a purchase
 * just completed) the main shape bursts and the whole cluster springs up and
 * back. Purely decorative, so it exposes no semantics.
 */
@Composable
internal fun ProHero(celebrate: Boolean, modifier: Modifier = Modifier) {
    val motion = MaterialTheme.motionScheme
    var clusterScale by remember { mutableFloatStateOf(1f) }
    LaunchedEffect(celebrate) {
        if (celebrate) {
            animate(1f, 1.18f, animationSpec = motion.fastSpatialSpec()) { v, _ -> clusterScale = v }
            animate(1.18f, 1f, animationSpec = motion.defaultSpatialSpec()) { v, _ -> clusterScale = v }
        }
    }
    val scheme = MaterialTheme.colorScheme

    Box(
        modifier = modifier
            .size(width = 200.dp, height = 190.dp)
            .graphicsLayer {
                scaleX = clusterScale
                scaleY = clusterScale
            }
            .clearAndSetSemantics { },
    ) {
        Box(
            modifier = Modifier.offset(x = 34.dp, y = 30.dp).size(132.dp),
            contentAlignment = Alignment.Center,
        ) {
            MorphingShape(
                polygons = if (celebrate) CelebrationShapes else MainShapes,
                color = scheme.primary,
                holdMillis = CYCLE_INTERVAL_MS,
                rotationStepDegrees = 30f,
                modifier = Modifier.size(132.dp),
            )
            Icon(
                imageVector = Icons.Filled.WorkspacePremium,
                contentDescription = null,
                tint = scheme.onPrimary,
                modifier = Modifier.size(56.dp),
            )
        }
        MorphingShape(
            polygons = TertiaryShapes,
            color = scheme.tertiaryContainer,
            holdMillis = CYCLE_INTERVAL_MS,
            startDelayMillis = 700,
            rotationStepDegrees = -50f,
            modifier = Modifier.align(Alignment.TopEnd).size(70.dp),
        )
        MorphingShape(
            polygons = SecondaryShapes,
            color = scheme.secondaryContainer,
            holdMillis = CYCLE_INTERVAL_MS,
            startDelayMillis = 1400,
            rotationStepDegrees = 70f,
            modifier = Modifier.align(Alignment.BottomStart).size(56.dp),
        )
    }
}

private const val CYCLE_INTERVAL_MS = 2200L

private val MainShapes = listOf(
    MaterialShapes.Sunny,
    MaterialShapes.Cookie9Sided,
    MaterialShapes.Burst,
    MaterialShapes.Cookie4Sided,
)
private val CelebrationShapes = listOf(MaterialShapes.Burst)
private val TertiaryShapes = listOf(MaterialShapes.Flower, MaterialShapes.Circle, MaterialShapes.Gem)
private val SecondaryShapes = listOf(MaterialShapes.Cookie6Sided, MaterialShapes.Triangle, MaterialShapes.Square)

@Preview(showBackground = true)
@Composable
private fun ProHeroPreview() {
    FastMaskTheme {
        Box(Modifier.size(240.dp), contentAlignment = Alignment.Center) { ProHero(celebrate = false) }
    }
}
