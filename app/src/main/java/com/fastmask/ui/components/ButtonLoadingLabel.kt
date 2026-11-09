package com.fastmask.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.LoadingIndicatorDefaults
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

enum class ButtonLoadingStyle {
    /** The label vanishes at once but stays announced to screen readers. */
    Steady,

    /** The label leaves the accessibility tree while loading. */
    Crossfade,
}

@Composable
fun ButtonLoadingLabel(
    loading: Boolean,
    loadingDescription: String,
    modifier: Modifier = Modifier,
    style: ButtonLoadingStyle = ButtonLoadingStyle.Steady,
    indicatorSize: Dp = 28.dp,
    label: @Composable () -> Unit,
) {
    val crossfade = style == ButtonLoadingStyle.Crossfade
    val motion = MaterialTheme.motionScheme
    val spatial: FiniteAnimationSpec<Float> =
        if (crossfade) motion.defaultSpatialSpec() else motion.fastSpatialSpec()
    val effects: FiniteAnimationSpec<Float> =
        if (crossfade) motion.defaultEffectsSpec() else motion.fastEffectsSpec()

    val labelModifier = if (crossfade) {
        val labelAlpha by animateFloatAsState(if (loading) 0f else 1f, effects, label = "labelAlpha")
        val labelScale by animateFloatAsState(if (loading) 0.6f else 1f, spatial, label = "labelScale")
        Modifier
            .graphicsLayer {
                alpha = labelAlpha
                scaleX = labelScale
                scaleY = labelScale
            }
            .then(if (loading) Modifier.clearAndSetSemantics { } else Modifier)
    } else {
        Modifier.graphicsLayer { alpha = if (loading) 0f else 1f }
    }

    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        Box(modifier = labelModifier) { label() }
        AnimatedVisibility(
            visible = loading,
            enter = fadeIn(effects) + scaleIn(spatial, initialScale = 0.6f),
            exit = fadeOut(effects) + scaleOut(spatial, targetScale = 0.6f),
        ) {
            LoadingIndicator(
                modifier = Modifier
                    .size(indicatorSize)
                    .semantics { contentDescription = loadingDescription },
                color = if (crossfade) LocalContentColor.current else LoadingIndicatorDefaults.indicatorColor,
            )
        }
    }
}

@Composable
fun ButtonLoadingLabel(
    loading: Boolean,
    loadingDescription: String,
    label: String,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    iconSize: Dp = 24.dp,
    iconSpacing: Dp = 8.dp,
    textStyle: TextStyle = LocalTextStyle.current,
    style: ButtonLoadingStyle = ButtonLoadingStyle.Steady,
) {
    ButtonLoadingLabel(
        loading = loading,
        loadingDescription = loadingDescription,
        modifier = modifier,
        style = style,
        indicatorSize = if (style == ButtonLoadingStyle.Crossfade) iconSize + 8.dp else 28.dp,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(iconSpacing),
        ) {
            if (icon != null) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    modifier = Modifier.size(iconSize),
                )
            }
            Text(text = label, style = textStyle)
        }
    }
}
