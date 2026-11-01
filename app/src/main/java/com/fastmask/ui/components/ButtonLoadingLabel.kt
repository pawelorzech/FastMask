package com.fastmask.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp

/**
 * Button content that swaps its label for a small [LoadingIndicator] while
 * [loading], without changing the button's size: the label stays in layout
 * (invisible) and the indicator is centred on top of it.
 */
@Composable
fun ButtonLoadingLabel(
    loading: Boolean,
    loadingDescription: String,
    modifier: Modifier = Modifier,
    label: @Composable () -> Unit,
) {
    val spatial: FiniteAnimationSpec<Float> = MaterialTheme.motionScheme.fastSpatialSpec()
    val effects: FiniteAnimationSpec<Float> = MaterialTheme.motionScheme.fastEffectsSpec()
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        Box(modifier = Modifier.graphicsLayer { alpha = if (loading) 0f else 1f }) { label() }
        AnimatedVisibility(
            visible = loading,
            enter = fadeIn(effects) + scaleIn(spatial, initialScale = 0.6f),
            exit = fadeOut(effects) + scaleOut(spatial, targetScale = 0.6f),
        ) {
            LoadingIndicator(
                modifier = Modifier
                    .size(28.dp)
                    .semantics { contentDescription = loadingDescription },
            )
        }
    }
}
