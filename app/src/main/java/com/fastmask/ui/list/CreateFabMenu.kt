package com.fastmask.ui.list

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Casino
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FloatingActionButtonMenu
import androidx.compose.material3.FloatingActionButtonMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.fastmask.R

internal class FabScrollState {
    var scrollingUp by mutableStateOf(true)
        private set

    val connection = object : NestedScrollConnection {
        override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
            if (available.y < -SCROLL_SLOP) scrollingUp = false
            else if (available.y > SCROLL_SLOP) scrollingUp = true
            return Offset.Zero
        }
    }

    private companion object {
        const val SCROLL_SLOP = 4f
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun CreateFabMenu(
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    extended: Boolean,
    isCreating: Boolean,
    onNewMask: () -> Unit,
    onRandomMask: () -> Unit,
    modifier: Modifier = Modifier,
    buttonModifier: Modifier = Modifier,
) {
    val haptic = LocalHapticFeedback.current
    val createDescription = stringResource(R.string.email_list_create_description)
    val closeDescription = stringResource(R.string.list_fab_close_menu)
    val newLabel = stringResource(R.string.email_list_create_fab)

    val motion = MaterialTheme.motionScheme
    val colorScheme = MaterialTheme.colorScheme
    val showLabel = extended && !expanded

    val containerColor by animateColorAsState(
        targetValue = if (expanded) colorScheme.primary else colorScheme.primaryContainer,
        animationSpec = motion.defaultEffectsSpec(),
        label = "fabContainer",
    )
    val contentColor by animateColorAsState(
        targetValue = if (expanded) colorScheme.onPrimary else colorScheme.onPrimaryContainer,
        animationSpec = motion.defaultEffectsSpec(),
        label = "fabContent",
    )
    val cornerRadius by animateDpAsState(
        targetValue = if (expanded) 28.dp else 16.dp,
        animationSpec = motion.fastSpatialSpec(),
        label = "fabCorner",
    )
    val iconRotation by animateFloatAsState(
        targetValue = if (expanded) 45f else 0f,
        animationSpec = motion.fastSpatialSpec(),
        label = "fabIconRotation",
    )

    val iconFadeIn = motion.fastEffectsSpec<Float>()
    val iconScaleIn = motion.fastSpatialSpec<Float>()
    val iconFadeOut = motion.fastEffectsSpec<Float>()
    val iconScaleOut = motion.fastSpatialSpec<Float>()

    val labelTextDescription = when {
        expanded -> closeDescription
        extended -> newLabel
        else -> createDescription
    }

    FloatingActionButtonMenu(
        expanded = expanded,
        modifier = modifier,
        button = {
            Surface(
                onClick = {
                    val opening = !expanded
                    haptic.performHapticFeedback(
                        if (opening) HapticFeedbackType.ToggleOn else HapticFeedbackType.ToggleOff,
                    )
                    onExpandedChange(opening)
                },
                shape = RoundedCornerShape(cornerRadius.coerceAtLeast(0.dp)),
                color = containerColor,
                contentColor = contentColor,
                shadowElevation = 6.dp,
                tonalElevation = 6.dp,
                modifier = buttonModifier
                    .defaultMinSize(minWidth = 56.dp, minHeight = 56.dp)
                    .semantics(mergeDescendants = true) {
                        role = Role.Button
                        contentDescription = labelTextDescription
                    },
            ) {
                Row(
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(horizontal = 16.dp),
                ) {
                    AnimatedContent(
                        targetState = isCreating && !expanded,
                        transitionSpec = {
                            (fadeIn(iconFadeIn) + scaleIn(iconScaleIn, initialScale = 0.6f)) togetherWith
                                (fadeOut(iconFadeOut) + scaleOut(iconScaleOut, targetScale = 0.6f))
                        },
                        label = "fabIcon",
                    ) { loading ->
                        if (loading) {
                            LoadingIndicator(
                                modifier = Modifier.size(24.dp),
                                color = LocalContentColor.current,
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Filled.Add,
                                contentDescription = null,
                                modifier = Modifier
                                    .size(24.dp)
                                    .graphicsLayer { rotationZ = iconRotation },
                            )
                        }
                    }

                    AnimatedVisibility(
                        visible = showLabel,
                        enter = expandHorizontally(
                            animationSpec = motion.defaultSpatialSpec(),
                            expandFrom = Alignment.Start,
                        ) + fadeIn(motion.defaultEffectsSpec()),
                        exit = shrinkHorizontally(
                            animationSpec = motion.fastSpatialSpec(),
                            shrinkTowards = Alignment.Start,
                        ) + fadeOut(motion.fastEffectsSpec()),
                    ) {
                        // Label is cleared from semantics so the button's accessible name comes
                        // solely from its contentDescription (WCAG 2.5.3 Label in Name) and does
                        // not collide with the "New mask" menu item when expanded.
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.clearAndSetSemantics { },
                        ) {
                            Spacer(Modifier.width(12.dp))
                            Text(
                                text = newLabel,
                                style = MaterialTheme.typography.labelLarge,
                                maxLines = 1,
                            )
                            Spacer(Modifier.width(4.dp))
                        }
                    }
                }
            }
        },
    ) {
        FloatingActionButtonMenuItem(
            onClick = onRandomMask,
            text = { Text(stringResource(R.string.list_random_mask)) },
            icon = { Icon(Icons.Filled.Casino, contentDescription = null) },
        )
        FloatingActionButtonMenuItem(
            onClick = onNewMask,
            text = { Text(newLabel) },
            icon = { Icon(Icons.Filled.Edit, contentDescription = null) },
        )
    }
}
