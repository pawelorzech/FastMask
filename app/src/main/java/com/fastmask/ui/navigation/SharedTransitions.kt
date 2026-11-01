package com.fastmask.ui.navigation

import androidx.compose.animation.AnimatedContentScope
import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Shape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.dp
import androidx.navigation.NamedNavArgument
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavDeepLink
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable

/**
 * Shared-element plumbing for the M3 Expressive container transform: a mask row
 * on the list grows into the detail screen and shrinks back into its row on back
 * (including the predictive back gesture).
 *
 * [FastMaskNavHost] wraps the graph in a `SharedTransitionLayout` and provides
 * [LocalSharedTransitionScope]; every destination registered with [screen]
 * provides its own [LocalNavAnimatedScope]. Screens only ever call
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
fun Modifier.maskContainer(id: String, shape: Shape = RoundedCornerShape(20.dp)): Modifier {
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

/**
 * `composable` that also provides [LocalNavAnimatedScope] to the destination, so
 * [maskContainer] can find the AnimatedContentScope of the navigation transition.
 */
fun NavGraphBuilder.screen(
    route: String,
    arguments: List<NamedNavArgument> = emptyList(),
    deepLinks: List<NavDeepLink> = emptyList(),
    enterTransition: (AnimatedContentTransitionScope<NavBackStackEntry>.() -> EnterTransition?)? = null,
    exitTransition: (AnimatedContentTransitionScope<NavBackStackEntry>.() -> ExitTransition?)? = null,
    popEnterTransition: (AnimatedContentTransitionScope<NavBackStackEntry>.() -> EnterTransition?)? = enterTransition,
    popExitTransition: (AnimatedContentTransitionScope<NavBackStackEntry>.() -> ExitTransition?)? = exitTransition,
    sizeTransform: (AnimatedContentTransitionScope<NavBackStackEntry>.() -> SizeTransform?)? = null,
    content: @Composable AnimatedContentScope.(NavBackStackEntry) -> Unit,
) {
    composable(
        route = route,
        arguments = arguments,
        deepLinks = deepLinks,
        enterTransition = enterTransition,
        exitTransition = exitTransition,
        popEnterTransition = popEnterTransition,
        popExitTransition = popExitTransition,
        sizeTransform = sizeTransform,
    ) { entry ->
        CompositionLocalProvider(LocalNavAnimatedScope provides this) {
            content(entry)
        }
    }
}
