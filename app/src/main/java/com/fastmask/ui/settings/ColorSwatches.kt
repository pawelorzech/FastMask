package com.fastmask.ui.settings

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.graphics.shapes.Morph
import androidx.graphics.shapes.RoundedPolygon
import com.fastmask.R
import com.fastmask.domain.model.Accent
import com.fastmask.ui.accessibility.radioButtonGroup
import com.fastmask.ui.theme.FastMaskTheme
import com.fastmask.ui.theme.swatchColor

/** Resting shape per swatch; the selected one morphs to [MaterialShapes.Sunny]. */
private fun Accent.restingShape(): RoundedPolygon = when (this) {
    Accent.DYNAMIC -> MaterialShapes.Circle
    Accent.AMBER -> MaterialShapes.Cookie4Sided
    Accent.INK -> MaterialShapes.Cookie9Sided
    Accent.SAGE -> MaterialShapes.Clover4Leaf
    Accent.PLUM -> MaterialShapes.Flower
    Accent.COBALT -> MaterialShapes.Pentagon
}

/**
 * Row of round color swatches: Wallpaper first (a four-quadrant mini of the
 * current scheme), then the five Pro seeds. The selected swatch morphs into a
 * Sunny and shows a check; for non-Pro users the seeds carry a lock badge and
 * [onLockedTap] fires instead of [onSelect].
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun ColorSwatchRow(
    selected: Accent,
    isPro: Boolean,
    onSelect: (Accent) -> Unit,
    onLockedTap: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val haptic = LocalHapticFeedback.current
    FlowRow(
        modifier = modifier.radioButtonGroup(),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Accent.entries.forEach { accent ->
            val locked = accent != Accent.DYNAMIC && !isPro
            ColorSwatch(
                accent = accent,
                selected = accent == selected,
                locked = locked,
                onClick = {
                    if (locked) {
                        haptic.performHapticFeedback(HapticFeedbackType.Reject)
                        onLockedTap()
                    } else if (accent != selected) {
                        haptic.performHapticFeedback(HapticFeedbackType.ToggleOn)
                        onSelect(accent)
                    }
                },
            )
        }
    }
}

@Composable
private fun ColorSwatch(
    accent: Accent,
    selected: Boolean,
    locked: Boolean,
    onClick: () -> Unit,
) {
    val motion = MaterialTheme.motionScheme
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()

    // Spatial springs overshoot, which is the point: the shape snaps past Sunny
    // and settles. Effects (alpha) never overshoot.
    val morphProgress by animateFloatAsState(
        targetValue = if (selected) 1f else 0f,
        animationSpec = motion.fastSpatialSpec(),
        label = "swatchMorph",
    )
    val pressScale by animateFloatAsState(
        targetValue = if (pressed) 0.86f else 1f,
        animationSpec = motion.fastSpatialSpec(),
        label = "swatchPress",
    )
    val checkScale by animateFloatAsState(
        targetValue = if (selected) 1f else 0f,
        animationSpec = motion.fastSpatialSpec(),
        label = "swatchCheckScale",
    )
    val checkAlpha by animateFloatAsState(
        targetValue = if (selected) 1f else 0f,
        animationSpec = motion.fastEffectsSpec(),
        label = "swatchCheckAlpha",
    )

    val morph = remember(accent) { Morph(accent.restingShape(), MaterialShapes.Sunny) }

    val name = stringResource(accent.displayNameRes)
    val lockedText = stringResource(R.string.settings_accent_locked)
    val scheme = MaterialTheme.colorScheme
    val dark = isSystemInDarkTheme()

    Box(
        modifier = Modifier
            .size(48.dp)
            .graphicsLayer {
                scaleX = pressScale
                scaleY = pressScale
            }
            .clip(CircleShape)
            .selectable(
                selected = selected,
                interactionSource = interaction,
                indication = ripple(),
                role = Role.RadioButton,
                onClick = onClick,
            )
            .semantics {
                contentDescription = name
                if (locked) stateDescription = lockedText
            },
        contentAlignment = Alignment.Center,
    ) {
        val seedColor = if (accent == Accent.DYNAMIC) Color.Unspecified else accent.swatchColor(dark)
        Box(
            modifier = Modifier
                .size(40.dp)
                .drawMorph(morph = { morph }, progress = { morphProgress }) { path ->
                    if (accent == Accent.DYNAMIC) {
                        // Mini of the current scheme: primary / tertiary /
                        // primary container / secondary container, one
                        // quadrant each, clipped to the swatch shape.
                        clipPath(path) {
                            val half = Size(size.width / 2f, size.height / 2f)
                            drawRect(scheme.primary, Offset.Zero, half)
                            drawRect(scheme.tertiary, Offset(half.width, 0f), half)
                            drawRect(scheme.primaryContainer, Offset(0f, half.height), half)
                            drawRect(scheme.secondaryContainer, Offset(half.width, half.height), half)
                        }
                    } else {
                        drawPath(path, seedColor)
                    }
                },
            contentAlignment = Alignment.Center,
        ) {
            val checkTint: Color
            val checkBackdrop: Boolean
            if (accent == Accent.DYNAMIC) {
                checkTint = scheme.primary
                checkBackdrop = true
            } else {
                checkTint = if (seedColor.luminance() > 0.5f) Color.Black else Color.White
                checkBackdrop = false
            }
            Box(
                modifier = Modifier
                    .size(24.dp)
                    .graphicsLayer {
                        scaleX = checkScale
                        scaleY = checkScale
                        alpha = checkAlpha
                    }
                    .then(
                        if (checkBackdrop) Modifier.clip(CircleShape).background(scheme.surface) else Modifier,
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Filled.Check,
                    contentDescription = null,
                    tint = checkTint,
                    modifier = Modifier.size(18.dp),
                )
            }
        }
        if (locked) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .size(20.dp)
                    .clip(CircleShape)
                    .background(scheme.surfaceContainerHighest),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Filled.Lock,
                    contentDescription = null,
                    tint = scheme.onSurfaceVariant,
                    modifier = Modifier.size(12.dp),
                )
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun ColorSwatchRowPreview() {
    FastMaskTheme {
        Box(Modifier.padding(16.dp)) {
            ColorSwatchRow(selected = Accent.DYNAMIC, isPro = false, onSelect = {}, onLockedTap = {})
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun ColorSwatchRowProPreview() {
    FastMaskTheme(accent = Accent.PLUM) {
        Box(Modifier.padding(16.dp)) {
            ColorSwatchRow(selected = Accent.PLUM, isPro = true, onSelect = {}, onLockedTap = {})
        }
    }
}
