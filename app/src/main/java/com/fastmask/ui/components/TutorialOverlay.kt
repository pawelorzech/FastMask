package com.fastmask.ui.components

import android.content.res.Configuration
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterExitState
import androidx.compose.animation.core.animateRectAsState
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
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.isTraversalGroup
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.traversalIndex
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.fastmask.R
import com.fastmask.ui.accessibility.politeLiveRegion
import com.fastmask.ui.theme.FastMaskTheme

/**
 * Where the tooltip card sits relative to the highlighted target.
 *
 * [AUTO] (what the list passes) puts the card at the bottom of the screen
 * unless the target is a compact element in the lower part of the screen — the
 * create FAB — in which case the card moves to the top so it never covers what
 * it is pointing at. [TOP] and [BOTTOM] force a side.
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

private val TooltipMaxWidth = 480.dp
private val TooltipPadding = 20.dp
private val ScreenEdgePadding = 16.dp
private val TooltipEdgeMargin = 24.dp
private val CutoutCornerRadius = 20.dp
private val CutoutPadding = 8.dp

/**
 * Full-screen scrim overlay with a punched-out spotlight around a target UI
 * element plus a stepped tooltip. The implementation is deliberately
 * library-free: a [Canvas] draws the scrim and clears the cutout using
 * [BlendMode.Clear], wrapped in [CompositingStrategy.Offscreen] so the
 * blend works without bleeding through. The spotlight glides between targets
 * on the theme's spatial spring, and the card is an M3 rich-tooltip: a
 * `surfaceContainerHigh` card with a title, supporting text and text actions.
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

    // The composable stays in composition across show/hide cycles (the caller
    // only flips `visible`), so `currentStep` outlived a completed tutorial.
    // A second demo session re-opened the overlay on its LAST card instead of
    // the first. Restart from step 1 every time it becomes visible again.
    LaunchedEffect(visible) {
        if (visible) {
            currentStep = 0
        }
    }

    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(MaterialTheme.motionScheme.defaultEffectsSpec()),
        exit = fadeOut(MaterialTheme.motionScheme.defaultEffectsSpec()),
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
                ) { /* swallow */ }
                // The scrim swallowed touches but not semantics, so a screen
                // reader kept walking the mask list, the FAB and the settings
                // button behind a 70% black overlay while the tutorial bubble
                // itself was never focused. Claiming traversal for this subtree
                // and sorting it first makes the overlay modal for the reader
                // the way it already is for the finger.
                .semantics {
                    isTraversalGroup = true
                    traversalIndex = -1f
                },
        ) {
            ScrimWithCutout(targetBounds = step.targetBounds)

            // Specs are read here: the transitionSpec lambda is not composable.
            val motion = MaterialTheme.motionScheme
            val slideInSpec = motion.defaultSpatialSpec<IntOffset>()
            val slideOutSpec = motion.fastSpatialSpec<IntOffset>()
            val fadeInSpec = motion.defaultEffectsSpec<Float>()
            val fadeOutSpec = motion.fastEffectsSpec<Float>()
            AnimatedContent(
                targetState = safeIndex,
                transitionSpec = {
                    val forward = targetState > initialState
                    val slideIn = slideInHorizontally(animationSpec = slideInSpec) { full ->
                        if (forward) full else -full
                    } + fadeIn(fadeInSpec)
                    val slideOut = slideOutHorizontally(animationSpec = slideOutSpec) { full ->
                        if (forward) -full else full
                    } + fadeOut(fadeOutSpec)
                    slideIn togetherWith slideOut
                },
                label = "tutorial-step",
                modifier = Modifier.fillMaxSize(),
            ) { idx ->
                val active = steps[idx]
                // AnimatedContent keeps both the outgoing and incoming
                // TooltipBubble mounted for the full crossfade. A live region
                // on the shared ancestor (the AnimatedContent container
                // itself) merged both instances' text, so TalkBack announced
                // the old step and the new step concatenated, then repeated
                // the new step again once the old one left composition.
                //
                // Scoping the live region to just the entering instance's
                // subtree, and stripping the exiting instance out of the
                // accessibility tree entirely, means only the new step's
                // title/body can ever be part of the announcement.
                val isEntering = transition.targetState == EnterExitState.Visible
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
                    modifier = if (isEntering) {
                        Modifier.politeLiveRegion()
                    } else {
                        // Not @ExperimentalComposeUiApi (unlike
                        // Modifier.semantics { invisibleToUser() }), and the
                        // repo doesn't opt into experimental Compose APIs
                        // elsewhere — this mirrors the same pattern already
                        // used to hide the background screen while the
                        // tutorial is up (MaskedEmailListScreen.kt).
                        Modifier.clearAndSetSemantics { }
                    },
                )
            }
        }
    }
}

@Composable
private fun ScrimWithCutout(targetBounds: Rect?) {
    val density = LocalDensity.current
    val padPx = with(density) { CutoutPadding.toPx() }
    val radiusPx = with(density) { CutoutCornerRadius.toPx() }
    val scrim = MaterialTheme.colorScheme.scrim.copy(alpha = 0.72f)

    // The spotlight glides from one target to the next on the spatial spring.
    // A step without a target draws no cutout at all (see below).
    val padded = targetBounds?.let {
        Rect(it.left - padPx, it.top - padPx, it.right + padPx, it.bottom + padPx)
    }
    val animated by animateRectAsState(
        targetValue = padded ?: Rect.Zero,
        animationSpec = MaterialTheme.motionScheme.defaultSpatialSpec<Rect>(),
        label = "spotlight",
    )

    Canvas(
        modifier = Modifier
            .fillMaxSize()
            .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen },
    ) {
        drawRect(color = scrim, size = size)
        if (padded != null) {
            val left = animated.left.coerceAtLeast(0f)
            val top = animated.top.coerceAtLeast(0f)
            val right = animated.right.coerceAtMost(size.width)
            val bottom = animated.bottom.coerceAtMost(size.height)
            val width = (right - left).coerceAtLeast(0f)
            val height = (bottom - top).coerceAtLeast(0f)
            if (width > 0f && height > 0f) {
                drawRoundRect(
                    color = Color.Transparent,
                    topLeft = Offset(left, top),
                    size = Size(width, height),
                    cornerRadius = CornerRadius(radiusPx, radiusPx),
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
    modifier: Modifier = Modifier,
) {
    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val screenHeight = constraints.maxHeight.toFloat()
        val bounds = step.targetBounds
        // Pointing at a compact element in the lower part of the screen (the
        // FAB): put the card above, otherwise it would sit on top of the
        // spotlight. A target that is most of the screen (the whole list) or
        // sits in the upper half keeps the card at the bottom, where the
        // thumb is. Targets are never chased around the screen: that is what
        // pushed the card off-screen in earlier revisions.
        val atTop = when (step.tooltipPosition) {
            TooltipPosition.TOP -> true
            TooltipPosition.BOTTOM -> false
            TooltipPosition.AUTO ->
                bounds != null && bounds.height < screenHeight * 0.4f && bounds.center.y > screenHeight * 0.55f
        }

        Surface(
            shape = MaterialTheme.shapes.extraLarge,
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            contentColor = MaterialTheme.colorScheme.onSurface,
            tonalElevation = 3.dp,
            shadowElevation = 6.dp,
            modifier = Modifier
                .align(if (atTop) Alignment.TopCenter else Alignment.BottomCenter)
                .then(if (atTop) Modifier.statusBarsPadding() else Modifier.navigationBarsPadding())
                .padding(
                    start = ScreenEdgePadding,
                    end = ScreenEdgePadding,
                    top = if (atTop) TooltipEdgeMargin else 0.dp,
                    bottom = if (atTop) 0.dp else TooltipEdgeMargin,
                )
                .fillMaxWidth()
                .widthIn(max = TooltipMaxWidth),
        ) {
            Column(modifier = Modifier.padding(TooltipPadding)) {
                Text(
                    text = step.title,
                    style = MaterialTheme.typography.titleMediumEmphasized,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    text = step.description,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(12.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    // Stock text buttons: Button role, 48dp target, press morph.
                    TextButton(onClick = onSkip) {
                        Text(
                            text = stringResource(R.string.tutorial_skip),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    TextButton(onClick = onNext) {
                        Text(
                            text = if (isLast) stringResource(R.string.tutorial_done) else stringResource(R.string.tutorial_next),
                            style = MaterialTheme.typography.labelLargeEmphasized,
                        )
                    }
                }
            }
        }
    }
}

private val previewSteps = listOf(
    TutorialStep(
        title = "Your masked emails",
        description = "Each address forwards mail to your inbox while hiding your real email.",
        targetBounds = Rect(Offset(40f, 400f), Size(1000f, 700f)),
    ),
    TutorialStep(
        title = "Create a new mask",
        description = "Tap here to generate a fresh address for any service.",
        targetBounds = Rect(Offset(700f, 1900f), Size(340f, 168f)),
    ),
)

@Preview(name = "Tutorial", showBackground = true, widthDp = 411, heightDp = 860)
@Composable
private fun TutorialOverlayPreview() {
    FastMaskTheme {
        TutorialOverlay(steps = previewSteps, visible = true, onComplete = {}, onSkip = {})
    }
}

@Preview(
    name = "Tutorial dark",
    showBackground = true,
    widthDp = 411,
    heightDp = 860,
    uiMode = Configuration.UI_MODE_NIGHT_YES,
)
@Composable
private fun TutorialOverlayDarkPreview() {
    FastMaskTheme {
        TutorialOverlay(steps = previewSteps.reversed(), visible = true, onComplete = {}, onSkip = {})
    }
}
