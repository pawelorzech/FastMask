package com.fastmask.ui.list

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Casino
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FloatingActionButtonMenu
import androidx.compose.material3.FloatingActionButtonMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.ToggleFloatingActionButton
import androidx.compose.material3.ToggleFloatingActionButtonDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.text
import androidx.compose.ui.unit.dp
import com.fastmask.R

/** Tracks scroll direction for the FAB without consuming any scroll. */
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

/**
 * The create FAB. Collapsed it is an extended FAB with a label (at the top of
 * the list / while scrolling up) or an icon-only toggle FAB (while scrolling
 * down); tapping opens a FAB menu with "Random mask" and "New mask", and the
 * toggle morphs its + into a ×.
 */
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
) {
    val haptic = LocalHapticFeedback.current
    val createDescription = stringResource(R.string.email_list_create_description)
    val closeDescription = stringResource(R.string.list_fab_close_menu)
    val newLabel = stringResource(R.string.email_list_create_fab)

    // Specs are read here: the transitionSpec lambda below is not composable.
    val motion = MaterialTheme.motionScheme
    val enterFade = motion.defaultEffectsSpec<Float>()
    val enterScale = motion.defaultSpatialSpec<Float>()
    val exitFade = motion.fastEffectsSpec<Float>()
    val exitScale = motion.fastSpatialSpec<Float>()

    FloatingActionButtonMenu(
        expanded = expanded,
        modifier = modifier,
        button = {
            AnimatedContent(
                targetState = extended && !expanded,
                transitionSpec = {
                    (fadeIn(enterFade) + scaleIn(enterScale, initialScale = 0.85f))
                        .togetherWith(fadeOut(exitFade) + scaleOut(exitScale, targetScale = 0.85f))
                },
                label = "fabVariant",
            ) { showExtended ->
                if (showExtended) {
                    ExtendedFloatingActionButton(
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.ToggleOn)
                            onExpandedChange(true)
                        },
                        // Material clears the label's semantics inside the extended FAB, so
                        // without a description the button has no name at all. It must
                        // equal the visible label (WCAG 2.5.3 Label in Name): "Create new
                        // masked address" is not "Nowa maska" in Polish.
                        modifier = Modifier.semantics { contentDescription = newLabel },
                        icon = {
                            if (isCreating) {
                                LoadingIndicator(modifier = Modifier.size(24.dp))
                            } else {
                                Icon(Icons.Filled.Add, contentDescription = null)
                            }
                        },
                        text = { Text(newLabel) },
                    )
                } else {
                    // The toggle starts unchecked and flips a frame later, so the
                    // + -> x morph plays when the menu opens from the extended FAB.
                    var checked by remember { mutableStateOf(false) }
                    LaunchedEffect(expanded) { checked = expanded }
                    ToggleFloatingActionButton(
                        checked = checked,
                        onCheckedChange = { open ->
                            haptic.performHapticFeedback(
                                if (open) HapticFeedbackType.ToggleOn else HapticFeedbackType.ToggleOff,
                            )
                            onExpandedChange(open)
                        },
                        modifier = Modifier.semantics {
                            contentDescription = if (expanded) closeDescription else createDescription
                        },
                    ) {
                        if (isCreating && !expanded) {
                            LoadingIndicator(modifier = Modifier.size(24.dp))
                        } else {
                            val icon by remember {
                                derivedStateOf { if (checkedProgress > 0.5f) Icons.Filled.Close else Icons.Filled.Add }
                            }
                            Icon(
                                imageVector = icon,
                                contentDescription = null,
                                modifier = with(ToggleFloatingActionButtonDefaults) {
                                    Modifier.animateIcon(checkedProgress = { checkedProgress })
                                },
                            )
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
