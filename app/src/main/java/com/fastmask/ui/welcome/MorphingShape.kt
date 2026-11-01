package com.fastmask.ui.welcome

import android.provider.Settings
import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.toPath
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import androidx.graphics.shapes.Morph
import androidx.graphics.shapes.RoundedPolygon
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** True when the user has turned system animations off (animator duration scale 0). */
@Composable
internal fun rememberMotionReduced(): Boolean {
    val context = LocalContext.current
    return remember(context) {
        runCatching {
            Settings.Global.getFloat(
                context.contentResolver,
                Settings.Global.ANIMATOR_DURATION_SCALE,
                1f,
            ) == 0f
        }.getOrDefault(false)
    }
}

/** The shape currently drawn: `from` -> `to`, interpolated by an [Animatable] outside this holder. */
private class MorphHolder(first: RoundedPolygon) {
    var to: RoundedPolygon = first
    var morph by mutableStateOf(Morph(first, first))
}

/**
 * A filled Material shape that morphs through [polygons] forever (while the
 * screen is resumed), turning a little on every step. Decorative: callers
 * clear semantics around it.
 *
 * - Several polygons: it waits [holdMillis] (after an initial [startDelayMillis]),
 *   morphs to the next one and repeats. Pausing is lifecycle-bound: nothing
 *   animates while the host is not RESUMED, and an interrupted morph is finished
 *   when it comes back.
 * - One polygon: it simply morphs to it (also when [polygons] changes later, which
 *   is how the lock screen turns the cookie into a burst on success).
 * - System animations off: it snaps to the first polygon and holds still.
 *
 * Morphs ride the theme's `slowSpatialSpec()` or, when [fast] is set,
 * `fastSpatialSpec()`; the spring overshoot is the "expressive" part.
 * [polygons] must be a remembered/stable list or the loop restarts every recomposition.
 */
@Composable
internal fun MorphingShape(
    polygons: List<RoundedPolygon>,
    color: Color,
    modifier: Modifier = Modifier,
    holdMillis: Long = 2400L,
    startDelayMillis: Long = 0L,
    rotationStepDegrees: Float = 0f,
    fast: Boolean = false,
    content: @Composable BoxScope.() -> Unit = {},
) {
    val reduced = rememberMotionReduced()
    val motion = MaterialTheme.motionScheme
    val spec = rememberUpdatedState(
        if (fast) motion.fastSpatialSpec<Float>() else motion.slowSpatialSpec<Float>(),
    )
    val hold = rememberUpdatedState(holdMillis)
    val step = rememberUpdatedState(rotationStepDegrees)

    val holder = remember { MorphHolder(polygons.first()) }
    val progress = remember { Animatable(1f) }
    val rotation = remember { Animatable(0f) }
    val path = remember { Path() }
    val lifecycle = LocalLifecycleOwner.current.lifecycle

    LaunchedEffect(polygons, reduced) {
        if (reduced) {
            holder.to = polygons.first()
            holder.morph = Morph(holder.to, holder.to)
            progress.snapTo(1f)
            return@LaunchedEffect
        }

        suspend fun morphTo(target: RoundedPolygon) {
            if (target === holder.to) return
            holder.morph = Morph(holder.to, target)
            holder.to = target
            progress.snapTo(0f)
            coroutineScope {
                if (step.value != 0f) {
                    launch { rotation.animateTo(rotation.value + step.value, spec.value) }
                }
                progress.animateTo(1f, spec.value)
            }
        }

        var firstRun = true
        lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            // A pause can cancel a morph half way; finish it before moving on.
            if (progress.value != 1f) progress.animateTo(1f, spec.value)
            if (holder.to !in polygons) morphTo(polygons.first())
            if (polygons.size > 1) {
                if (firstRun) delay(startDelayMillis)
                firstRun = false
                while (true) {
                    delay(hold.value)
                    val next = (polygons.indexOf(holder.to) + 1) % polygons.size
                    morphTo(polygons[next])
                }
            }
        }
    }

    Box(
        modifier = modifier.drawBehind {
            path.rewind()
            holder.morph.toPath(progress = progress.value, path = path)
            withTransform({
                rotate(degrees = rotation.value, pivot = center)
                // MaterialShapes polygons live in a 1x1 box.
                scale(scaleX = size.width, scaleY = size.height, pivot = Offset.Zero)
            }) {
                drawPath(path, color)
            }
        },
        contentAlignment = Alignment.Center,
        content = content,
    )
}
