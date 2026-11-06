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

/**
 * Shared-element plumbing for the M3 Expressive container transform: a mask row
 * on the list grows into the detail screen and shrinks back into its row on back
 * (including the predictive back gesture).
 *
 * `FastMaskNavHost` wraps the graph in a `SharedTransitionLayout` and provides
 * [LocalSharedTransitionScope]; every destination registered with its `screen`
 * helper provides its own [LocalNavAnimatedScope]. Screens only ever call
 * [maskContainer] — both locals are null in previews and tests, where it is a no-op.
 */
val LocalSharedTransitionScope = staticCompositionLocalOf<SharedTransitionScope?> { null }
val LocalNavAnimatedScope = staticCompositionLocalOf<AnimatedVisibilityScope?> { null }

/** Key shared by the list row and the detail screen's root container for mask [id]. */
fun maskContainerKey(id: String) = "mask-container-$id"

/**
 * Marks this node as the container of mask [id]. Put it on the list row's outer
 * container and on the detail screen's root surface; pass the row's resting
 * [shape] so the transform clips with the right corners while it flies.
 */
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
