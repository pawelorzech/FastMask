package com.fastmask.ui.navigation

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.AnimatedContentTransitionScope.SlideDirection
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.ui.unit.IntOffset
import androidx.navigation.NavBackStackEntry

internal enum class NavTransitionStyle {
    HIERARCHY_FORWARD,
    HIERARCHY_BACKWARD,
    STEP_BACK_RESET,
    MODAL_OPEN,
    MODAL_CLOSE,
    SESSION_ENTER,
    SESSION_EXIT,
}

internal enum class TransitionLayerRole {
    NONE,
    UNDERNEATH,
    TOP_HORIZONTAL,
    TOP_MODAL,
}

private val WorkspaceRoutes = setOf(
    NavRoutes.EMAIL_LIST,
    NavRoutes.EMAIL_DETAIL,
    NavRoutes.SETTINGS,
    NavRoutes.CREATE_EMAIL,
    NavRoutes.PRO,
)

private fun isModalDestination(route: String?, isModalLogin: Boolean): Boolean =
    route == NavRoutes.CREATE_EMAIL ||
        route == NavRoutes.PRO ||
        (route == NavRoutes.LOGIN && isModalLogin)

/**
 * Pure transition classifier mapping a `(from, to, isPop)` navigation edge to its
 * Material 3 Expressive motion role so forward, backward, modal and session transitions
 * are always logically paired.
 */
internal fun resolveTransitionStyle(
    fromRoute: String?,
    toRoute: String?,
    isPop: Boolean,
    fromIsModalLogin: Boolean = false,
    toIsModalLogin: Boolean = false,
): NavTransitionStyle {
    if (isPop) {
        return if (isModalDestination(fromRoute, fromIsModalLogin)) {
            NavTransitionStyle.MODAL_CLOSE
        } else {
            NavTransitionStyle.HIERARCHY_BACKWARD
        }
    }

    if (isModalDestination(toRoute, toIsModalLogin)) {
        return NavTransitionStyle.MODAL_OPEN
    }

    if ((fromRoute == NavRoutes.WELCOME || fromRoute == NavRoutes.LOGIN) && toRoute == NavRoutes.EMAIL_LIST) {
        return NavTransitionStyle.SESSION_ENTER
    }

    if (fromRoute == NavRoutes.LOGIN && toRoute == NavRoutes.WELCOME) {
        return NavTransitionStyle.STEP_BACK_RESET
    }

    if (fromRoute in WorkspaceRoutes &&
        (toRoute == NavRoutes.WELCOME || (toRoute == NavRoutes.LOGIN && !toIsModalLogin))
    ) {
        return NavTransitionStyle.SESSION_EXIT
    }

    return NavTransitionStyle.HIERARCHY_FORWARD
}

internal fun NavTransitionStyle.enterLayerRole(): TransitionLayerRole = when (this) {
    NavTransitionStyle.HIERARCHY_FORWARD,
    NavTransitionStyle.STEP_BACK_RESET,
    -> TransitionLayerRole.TOP_HORIZONTAL

    NavTransitionStyle.HIERARCHY_BACKWARD,
    NavTransitionStyle.MODAL_CLOSE,
    -> TransitionLayerRole.UNDERNEATH

    NavTransitionStyle.MODAL_OPEN -> TransitionLayerRole.TOP_MODAL

    NavTransitionStyle.SESSION_ENTER,
    NavTransitionStyle.SESSION_EXIT,
    -> TransitionLayerRole.NONE
}

internal fun NavTransitionStyle.exitLayerRole(): TransitionLayerRole = when (this) {
    NavTransitionStyle.HIERARCHY_FORWARD,
    NavTransitionStyle.STEP_BACK_RESET,
    NavTransitionStyle.MODAL_OPEN,
    -> TransitionLayerRole.UNDERNEATH

    NavTransitionStyle.HIERARCHY_BACKWARD -> TransitionLayerRole.TOP_HORIZONTAL

    NavTransitionStyle.MODAL_CLOSE -> TransitionLayerRole.TOP_MODAL

    NavTransitionStyle.SESSION_ENTER,
    NavTransitionStyle.SESSION_EXIT,
    -> TransitionLayerRole.NONE
}

internal fun NavBackStackEntry.isModalLogin(): Boolean =
    destination.route == NavRoutes.LOGIN &&
        arguments?.getBoolean(NavRoutes.ARG_LOGIN_MODAL) == true

@Immutable
internal class NavMotionSpecs(
    val horizontalSlide: FiniteAnimationSpec<IntOffset>,
    val verticalSlide: FiniteAnimationSpec<IntOffset>,
    val defaultSpatialFloat: FiniteAnimationSpec<Float>,
    val slowSpatialFloat: FiniteAnimationSpec<Float>,
    val defaultEffectsFloat: FiniteAnimationSpec<Float>,
)

@Composable
internal fun rememberNavMotionSpecs(): NavMotionSpecs {
    val scheme = MaterialTheme.motionScheme
    val defaultSpatial = scheme.defaultSpatialSpec<Float>()
    val slowSpatial = scheme.slowSpatialSpec<Float>()
    val defaultEffects = scheme.defaultEffectsSpec<Float>()
    return remember(defaultSpatial, slowSpatial, defaultEffects) {
        NavMotionSpecs(
            // Critically damped springs for full-screen edge slides prevent overshoot past 0px
            // that would expose a narrow gap along the trailing or bottom screen edge.
            horizontalSlide = spring(
                dampingRatio = Spring.DampingRatioNoBouncy,
                stiffness = 380f,
            ),
            verticalSlide = spring(
                dampingRatio = Spring.DampingRatioNoBouncy,
                stiffness = 340f,
            ),
            defaultSpatialFloat = defaultSpatial,
            slowSpatialFloat = slowSpatial,
            defaultEffectsFloat = defaultEffects,
        )
    }
}

internal fun AnimatedContentTransitionScope<NavBackStackEntry>.resolveStyle(isPop: Boolean): NavTransitionStyle =
    resolveTransitionStyle(
        fromRoute = initialState.destination.route,
        toRoute = targetState.destination.route,
        isPop = isPop,
        fromIsModalLogin = initialState.isModalLogin(),
        toIsModalLogin = targetState.isModalLogin(),
    )

internal fun AnimatedContentTransitionScope<NavBackStackEntry>.enterTransitionFor(
    style: NavTransitionStyle,
    specs: NavMotionSpecs,
): EnterTransition = when (style) {
    NavTransitionStyle.HIERARCHY_FORWARD ->
        slideIntoContainer(SlideDirection.Start, specs.horizontalSlide)

    NavTransitionStyle.HIERARCHY_BACKWARD ->
        slideIntoContainer(SlideDirection.End, specs.horizontalSlide) { it / 4 }

    NavTransitionStyle.STEP_BACK_RESET ->
        slideIntoContainer(SlideDirection.End, specs.horizontalSlide)

    NavTransitionStyle.MODAL_OPEN ->
        slideIntoContainer(SlideDirection.Up, specs.verticalSlide)

    NavTransitionStyle.MODAL_CLOSE ->
        scaleIn(specs.defaultSpatialFloat, initialScale = 0.94f)

    NavTransitionStyle.SESSION_ENTER ->
        scaleIn(specs.slowSpatialFloat, initialScale = 0.90f) + fadeIn(specs.defaultEffectsFloat)

    NavTransitionStyle.SESSION_EXIT ->
        scaleIn(specs.slowSpatialFloat, initialScale = 1.06f) + fadeIn(specs.defaultEffectsFloat)
}

internal fun AnimatedContentTransitionScope<NavBackStackEntry>.exitTransitionFor(
    style: NavTransitionStyle,
    specs: NavMotionSpecs,
): ExitTransition = when (style) {
    NavTransitionStyle.HIERARCHY_FORWARD ->
        slideOutOfContainer(SlideDirection.Start, specs.horizontalSlide) { it / 4 }

    NavTransitionStyle.HIERARCHY_BACKWARD ->
        slideOutOfContainer(SlideDirection.End, specs.horizontalSlide)

    NavTransitionStyle.STEP_BACK_RESET ->
        slideOutOfContainer(SlideDirection.End, specs.horizontalSlide) { it / 4 }

    NavTransitionStyle.MODAL_OPEN ->
        scaleOut(specs.defaultSpatialFloat, targetScale = 0.94f)

    NavTransitionStyle.MODAL_CLOSE ->
        slideOutOfContainer(SlideDirection.Down, specs.verticalSlide)

    NavTransitionStyle.SESSION_ENTER ->
        scaleOut(specs.slowSpatialFloat, targetScale = 1.08f) + fadeOut(specs.defaultEffectsFloat)

    NavTransitionStyle.SESSION_EXIT ->
        scaleOut(specs.slowSpatialFloat, targetScale = 0.90f) + fadeOut(specs.defaultEffectsFloat)
}
