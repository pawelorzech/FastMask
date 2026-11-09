package com.fastmask.ui.components.expressive

import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Shape

val LocalSharedTransitionScope = staticCompositionLocalOf<SharedTransitionScope?> { null }
val LocalNavAnimatedScope = staticCompositionLocalOf<AnimatedVisibilityScope?> { null }

private fun maskContainerKey(id: String) = "mask-container-$id"

@Composable
fun Modifier.maskContainer(id: String, shape: Shape = RoundedCornerShape(SegmentOuterCorner)): Modifier {
    val sharedScope = LocalSharedTransitionScope.current ?: return this
    val animatedScope = LocalNavAnimatedScope.current ?: return this
    val spec: FiniteAnimationSpec<Rect> = MaterialTheme.motionScheme.defaultSpatialSpec()
    return with(sharedScope) {
        this@maskContainer.sharedBounds(
            sharedContentState = rememberSharedContentState(key = maskContainerKey(id)),
            animatedVisibilityScope = animatedScope,
            boundsTransform = { _, _ -> spec },
            resizeMode = SharedTransitionScope.ResizeMode.RemeasureToBounds,
            clipInOverlayDuringTransition = OverlayClip(shape),
        )
    }
}
