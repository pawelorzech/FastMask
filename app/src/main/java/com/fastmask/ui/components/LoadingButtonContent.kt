package com.fastmask.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Content for an expressive button that waits: the label (and optional icon)
 * swap for a small [LoadingIndicator] in the content color, with the theme's
 * springs, while the button keeps its size. The indicator carries
 * [loadingDescription] so a screen reader still hears what the button is doing.
 */
@Composable
internal fun RowScope.LoadingButtonContent(
    loading: Boolean,
    loadingDescription: String,
    label: String,
    icon: ImageVector? = null,
    iconSize: Dp = 24.dp,
    iconSpacing: Dp = 8.dp,
) {
    val spatial = MaterialTheme.motionScheme.defaultSpatialSpec<Float>()
    val effects = MaterialTheme.motionScheme.defaultEffectsSpec<Float>()
    AnimatedContent(
        targetState = loading,
        transitionSpec = {
            (fadeIn(effects) + scaleIn(spatial, initialScale = 0.6f)) togetherWith
                (fadeOut(effects) + scaleOut(spatial, targetScale = 0.6f))
        },
        label = "buttonLoading",
    ) { isLoading ->
        if (isLoading) {
            LoadingIndicator(
                modifier = Modifier
                    .size(iconSize + 8.dp)
                    .semantics { contentDescription = loadingDescription },
                color = LocalContentColor.current,
            )
        } else {
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
                Text(text = label)
            }
        }
    }
}
