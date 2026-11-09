package com.fastmask.ui.detail

import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.IntSize

/** `transitionSpec` lambdas are not composable, so they capture these specs instead of reading `MaterialTheme`. */
internal class Motion(
    val fastEffects: FiniteAnimationSpec<Float>,
    val defaultEffects: FiniteAnimationSpec<Float>,
    val fastSpatial: FiniteAnimationSpec<Float>,
    val defaultSpatial: FiniteAnimationSpec<Float>,
    val defaultSpatialSize: FiniteAnimationSpec<IntSize>,
)

@Composable
internal fun rememberMotion(): Motion {
    val scheme = MaterialTheme.motionScheme
    return Motion(
        fastEffects = scheme.fastEffectsSpec(),
        defaultEffects = scheme.defaultEffectsSpec(),
        fastSpatial = scheme.fastSpatialSpec(),
        defaultSpatial = scheme.defaultSpatialSpec(),
        defaultSpatialSize = scheme.defaultSpatialSpec(),
    )
}
