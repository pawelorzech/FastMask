package com.fastmask.ui.pro

import android.provider.Settings
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.graphics.shapes.Morph
import androidx.graphics.shapes.RoundedPolygon
import com.fastmask.ui.settings.drawMorph
import com.fastmask.ui.theme.FastMaskTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

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
            MorphingBlob(
                sequence = listOf(
                    MaterialShapes.Sunny,
                    MaterialShapes.Cookie9Sided,
                    MaterialShapes.Burst,
                    MaterialShapes.Cookie4Sided,
                ),
                startDelayMs = 0,
                color = scheme.primary,
                spinDegrees = 30f,
                size = 132.dp,
                target = if (celebrate) MaterialShapes.Burst else null,
            )
            Icon(
                imageVector = Icons.Filled.WorkspacePremium,
                contentDescription = null,
                tint = scheme.onPrimary,
                modifier = Modifier.size(56.dp),
            )
        }
        MorphingBlob(
            sequence = listOf(MaterialShapes.Flower, MaterialShapes.Circle, MaterialShapes.Gem),
            startDelayMs = 700,
            color = scheme.tertiaryContainer,
            spinDegrees = -50f,
            size = 70.dp,
            modifier = Modifier.align(Alignment.TopEnd),
        )
        MorphingBlob(
            sequence = listOf(MaterialShapes.Cookie6Sided, MaterialShapes.Triangle, MaterialShapes.Square),
            startDelayMs = 1400,
            color = scheme.secondaryContainer,
            spinDegrees = 70f,
            size = 56.dp,
            modifier = Modifier.align(Alignment.BottomStart),
        )
    }
}

private const val CYCLE_INTERVAL_MS = 2200L

/** True unless the user turned system animations off (animator scale 0). */
@Composable
private fun rememberAnimationsEnabled(): Boolean {
    val resolver = LocalContext.current.contentResolver
    return remember(resolver) {
        runCatching {
            Settings.Global.getFloat(resolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) != 0f
        }.getOrDefault(true)
    }
}

/**
 * A filled shape that walks through [sequence], morphing with a slow spatial
 * spring and turning by [spinDegrees] each step. A non-null [target] stops the
 * cycle and morphs there instead.
 */
@Composable
private fun MorphingBlob(
    sequence: List<RoundedPolygon>,
    startDelayMs: Long,
    color: Color,
    spinDegrees: Float,
    size: Dp,
    modifier: Modifier = Modifier,
    target: RoundedPolygon? = null,
) {
    val motion = MaterialTheme.motionScheme
    val animationsEnabled = rememberAnimationsEnabled()
    var morph by remember { mutableStateOf(Morph(sequence.first(), sequence.first())) }
    var progress by remember { mutableFloatStateOf(1f) }
    var rotation by remember { mutableFloatStateOf(0f) }
    var current by remember { mutableStateOf(sequence.first()) }

    LaunchedEffect(target, animationsEnabled) {
        suspend fun moveTo(next: RoundedPolygon) {
            // One snapshot, so no frame draws the new morph at the old progress.
            Snapshot.withMutableSnapshot {
                morph = Morph(current, next)
                progress = 0f
            }
            current = next
            val from = rotation
            launch {
                animate(from, from + spinDegrees, animationSpec = motion.slowSpatialSpec()) { v, _ -> rotation = v }
            }
            animate(0f, 1f, animationSpec = motion.slowSpatialSpec()) { v, _ -> progress = v }
        }

        if (target != null) {
            moveTo(target)
            return@LaunchedEffect
        }
        if (!animationsEnabled) return@LaunchedEffect
        delay(startDelayMs)
        var i = sequence.indexOf(current).coerceAtLeast(0)
        while (true) {
            delay(CYCLE_INTERVAL_MS)
            i = (i + 1) % sequence.size
            moveTo(sequence[i])
        }
    }

    Box(
        modifier = modifier
            .size(size)
            .graphicsLayer { rotationZ = rotation }
            .drawMorph(morph = { morph }, progress = { progress }) { path -> drawPath(path, color) },
    )
}

@Preview(showBackground = true)
@Composable
private fun ProHeroPreview() {
    FastMaskTheme {
        Box(Modifier.size(240.dp), contentAlignment = Alignment.Center) { ProHero(celebrate = false) }
    }
}
