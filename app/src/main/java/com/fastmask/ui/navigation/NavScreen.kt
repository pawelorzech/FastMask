package com.fastmask.ui.navigation

import androidx.compose.animation.AnimatedContentScope
import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.EnterExitState
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.spring
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.navigation.NamedNavArgument
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable

internal fun NavGraphBuilder.screen(
    route: String,
    arguments: List<NamedNavArgument> = emptyList(),
    enterTransition: (AnimatedContentTransitionScope<NavBackStackEntry>.() -> EnterTransition?)? = null,
    exitTransition: (AnimatedContentTransitionScope<NavBackStackEntry>.() -> ExitTransition?)? = null,
    popEnterTransition: (AnimatedContentTransitionScope<NavBackStackEntry>.() -> EnterTransition?)? = null,
    popExitTransition: (AnimatedContentTransitionScope<NavBackStackEntry>.() -> ExitTransition?)? = null,
    layerRole: (String) -> TransitionLayerRole = { TransitionLayerRole.NONE },
    onDisposeEntry: (String) -> Unit = {},
    content: @Composable AnimatedContentScope.(NavBackStackEntry) -> Unit,
) {
    composable(
        route = route,
        arguments = arguments,
        enterTransition = enterTransition,
        exitTransition = exitTransition,
        popEnterTransition = popEnterTransition,
        popExitTransition = popExitTransition,
    ) { entry ->
        DisposableEffect(entry.id) {
            onDispose { onDisposeEntry(entry.id) }
        }
        ScreenTransitionLayer(
            entryId = entry.id,
            layerRole = layerRole,
        ) {
            content(entry)
        }
    }
}

@Composable
private fun AnimatedContentScope.ScreenTransitionLayer(
    entryId: String,
    layerRole: (String) -> TransitionLayerRole,
    content: @Composable () -> Unit,
) {
    val dark = isSystemInDarkTheme()
    val maxScrim = if (dark) 0.28f else 0.12f
    val outlineColor = MaterialTheme.colorScheme.outlineVariant
    val progressSpec = remember {
        spring<Float>(
            dampingRatio = Spring.DampingRatioNoBouncy,
            stiffness = 380f,
        )
    }
    val density = LocalDensity.current
    val modalCornerPx = with(density) { 24.dp.toPx() }
    val shadowSpanPx = with(density) { 12.dp.toPx() }
    val borderPx = with(density) { 1.dp.toPx() }

    val offScreenProgress by transition.animateFloat(
        transitionSpec = { progressSpec },
        label = "screenLayerProgress",
    ) { state ->
        if (state == EnterExitState.Visible) 0f else 1f
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .graphicsLayer {
                val role = layerRole(entryId)
                val edgeAlpha = (offScreenProgress * 12f).coerceIn(0f, 1f)
                if (role == TransitionLayerRole.TOP_MODAL && edgeAlpha > 0.02f) {
                    val corner = modalCornerPx * edgeAlpha
                    shape = RoundedCornerShape(topStart = corner, topEnd = corner)
                    clip = true
                } else {
                    clip = false
                }
            }
            .drawWithContent {
                drawContent()
                val role = layerRole(entryId)
                when (role) {
                    TransitionLayerRole.NONE -> Unit
                    TransitionLayerRole.UNDERNEATH -> {
                        val scrimAlpha = (maxScrim * offScreenProgress).coerceIn(0f, maxScrim)
                        if (scrimAlpha > 0.001f) {
                            drawRect(Color.Black.copy(alpha = scrimAlpha))
                        }
                    }

                    TransitionLayerRole.TOP_HORIZONTAL -> {
                        val edgeAlpha = (offScreenProgress * 12f).coerceIn(0f, 1f)
                        if (edgeAlpha > 0.001f) {
                            val shadowColor = Color.Black.copy(alpha = (if (dark) 0.36f else 0.14f) * edgeAlpha)
                            if (layoutDirection == LayoutDirection.Ltr) {
                                drawRect(
                                    brush = Brush.horizontalGradient(
                                        colors = listOf(Color.Transparent, shadowColor),
                                        startX = -shadowSpanPx,
                                        endX = 0f,
                                    ),
                                    topLeft = Offset(-shadowSpanPx, 0f),
                                    size = Size(shadowSpanPx, size.height),
                                )
                                drawRect(
                                    color = outlineColor.copy(alpha = 0.6f * edgeAlpha),
                                    topLeft = Offset.Zero,
                                    size = Size(borderPx, size.height),
                                )
                            } else {
                                drawRect(
                                    brush = Brush.horizontalGradient(
                                        colors = listOf(shadowColor, Color.Transparent),
                                        startX = size.width,
                                        endX = size.width + shadowSpanPx,
                                    ),
                                    topLeft = Offset(size.width, 0f),
                                    size = Size(shadowSpanPx, size.height),
                                )
                                drawRect(
                                    color = outlineColor.copy(alpha = 0.6f * edgeAlpha),
                                    topLeft = Offset(size.width - borderPx, 0f),
                                    size = Size(borderPx, size.height),
                                )
                            }
                        }
                    }

                    TransitionLayerRole.TOP_MODAL -> {
                        val edgeAlpha = (offScreenProgress * 12f).coerceIn(0f, 1f)
                        if (edgeAlpha > 0.001f) {
                            drawRect(
                                color = outlineColor.copy(alpha = 0.65f * edgeAlpha),
                                topLeft = Offset.Zero,
                                size = Size(size.width, borderPx),
                            )
                        }
                    }
                }
            },
    ) {
        content()
    }
}
