package com.fastmask.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.fastmask.R
import com.fastmask.ui.theme.FastMaskExtras

/**
 * Position of the tooltip relative to the highlighted target.
 *
 * Retained for source compatibility with callers, but the current overlay
 * implementation pins the tooltip to a fixed position at the bottom of the
 * screen regardless of this value. Targets that don't fit comfortably
 * above the tooltip are still highlighted via the spotlight cutout — the
 * tooltip itself simply doesn't try to chase them around the screen.
 */
enum class TooltipPosition { TOP, BOTTOM, AUTO }

data class TutorialStep(
    val title: String,
    val description: String,
    /**
     * Target rectangle in root coordinates. If `null`, the step shows the
     * scrim with no cutout (a plain modal) — used for steps that don't have
     * a single UI element to highlight.
     */
    val targetBounds: Rect?,
    val tooltipPosition: TooltipPosition = TooltipPosition.AUTO,
)

private val ScrimColor = Color.Black.copy(alpha = 0.7f)
private const val ANIM_DURATION_MS = 300
private val TooltipMaxWidth = 480.dp
private val TooltipPadding = 16.dp
private val ScreenEdgePadding = 20.dp
private val TooltipBottomMargin = 32.dp
private val CutoutCornerRadius = 12f
private val CutoutPadding = 6f

/**
 * Full-screen scrim overlay with a punched-out cutout around a target UI
 * element plus a stepped tooltip. The implementation is deliberately
 * library-free: a [Canvas] draws the scrim and clears the cutout using
 * [BlendMode.Clear], wrapped in [CompositingStrategy.Offscreen] so the
 * blend works without bleeding through.
 *
 * The caller owns whether the overlay is visible and what happens on
 * complete/skip — typical usage is to flip a `tutorialCompleted` flag in
 * persistent state.
 */
@Composable
fun TutorialOverlay(
    steps: List<TutorialStep>,
    visible: Boolean,
    onComplete: () -> Unit,
    onSkip: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (steps.isEmpty()) return

    var currentStep by remember { mutableIntStateOf(0) }

    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(animationSpec = tween(ANIM_DURATION_MS)),
        exit = fadeOut(animationSpec = tween(ANIM_DURATION_MS)),
    ) {
        val safeIndex = currentStep.coerceIn(0, steps.lastIndex)
        val step = steps[safeIndex]

        Box(
            modifier = modifier
                .fillMaxSize()
                // Consume taps so the underlying screen is inert while the
                // tutorial is up; users must use the explicit buttons.
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                ) { /* swallow */ },
        ) {
            ScrimWithCutout(targetBounds = step.targetBounds)

            AnimatedContent(
                targetState = safeIndex,
                transitionSpec = {
                    val forward = targetState > initialState
                    val slideIn = slideInHorizontally(animationSpec = tween(ANIM_DURATION_MS)) {
                        full -> if (forward) full else -full
                    } + fadeIn(animationSpec = tween(ANIM_DURATION_MS))
                    val slideOut = slideOutHorizontally(animationSpec = tween(ANIM_DURATION_MS)) {
                        full -> if (forward) -full else full
                    } + fadeOut(animationSpec = tween(ANIM_DURATION_MS))
                    slideIn togetherWith slideOut
                },
                label = "tutorial-step",
                modifier = Modifier.fillMaxSize(),
            ) { idx ->
                val active = steps[idx]
                TooltipBubble(
                    step = active,
                    isLast = idx == steps.lastIndex,
                    onSkip = onSkip,
                    onNext = {
                        if (idx == steps.lastIndex) {
                            onComplete()
                        } else {
                            currentStep = idx + 1
                        }
                    },
                )
            }
        }
    }
}

@Composable
private fun ScrimWithCutout(targetBounds: Rect?) {
    Canvas(
        modifier = Modifier
            .fillMaxSize()
            .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen },
    ) {
        drawRect(color = ScrimColor, size = size)
        if (targetBounds != null) {
            val left = (targetBounds.left - CutoutPadding).coerceAtLeast(0f)
            val top = (targetBounds.top - CutoutPadding).coerceAtLeast(0f)
            val right = (targetBounds.right + CutoutPadding).coerceAtMost(size.width)
            val bottom = (targetBounds.bottom + CutoutPadding).coerceAtMost(size.height)
            val width = (right - left).coerceAtLeast(0f)
            val height = (bottom - top).coerceAtLeast(0f)
            if (width > 0f && height > 0f) {
                drawRoundRect(
                    color = Color.Transparent,
                    topLeft = Offset(left, top),
                    size = Size(width, height),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(CutoutCornerRadius, CutoutCornerRadius),
                    blendMode = BlendMode.Clear,
                )
            }
        }
    }
}

@Composable
private fun TooltipBubble(
    step: TutorialStep,
    isLast: Boolean,
    onSkip: () -> Unit,
    onNext: () -> Unit,
) {
    val extras = FastMaskExtras.current

    // Fixed bottom-aligned tooltip. Earlier revisions tried to chase the
    // highlighted target with dynamic top/bottom placement, but with full-
    // height targets (e.g. the whole list view) the computed Y ended up
    // off-screen and the tooltip never appeared. Pinning it to the bottom
    // makes rendering deterministic — the spotlight cutout still visually
    // points at the target.
    Box(
        modifier = Modifier
            .fillMaxSize()
            .navigationBarsPadding(),
    ) {
        Surface(
            shape = RoundedCornerShape(14.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 8.dp,
            shadowElevation = 8.dp,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(
                    start = ScreenEdgePadding,
                    end = ScreenEdgePadding,
                    bottom = TooltipBottomMargin,
                )
                .fillMaxWidth()
                .widthIn(max = TooltipMaxWidth),
        ) {
            Column(modifier = Modifier.padding(TooltipPadding)) {
                Text(
                    text = step.title,
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    text = step.description,
                    style = MaterialTheme.typography.bodyMedium,
                    color = extras.inkSoft,
                )
                Spacer(Modifier.height(14.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = stringResource(R.string.tutorial_skip),
                        style = MaterialTheme.typography.labelLarge,
                        color = extras.inkMuted,
                        modifier = Modifier
                            .clip(RoundedCornerShape(10.dp))
                            .clickable(onClick = onSkip)
                            .padding(horizontal = 10.dp, vertical = 6.dp),
                    )
                    PillButton(
                        text = if (isLast) stringResource(R.string.tutorial_done) else stringResource(R.string.tutorial_next),
                        onClick = onNext,
                        variant = PillButtonVariant.Primary,
                    )
                }
            }
        }
    }
}

