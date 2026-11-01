package com.fastmask.ui.lock

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.fastmask.R
import com.fastmask.ui.accessibility.screenHeading
import com.fastmask.ui.theme.FastMaskTheme
import com.fastmask.ui.welcome.MorphingShape
import kotlinx.coroutines.delay

/** How long the success burst stays on screen before the content behind the gate is composed. */
internal const val UnlockSuccessMillis = 550L

/**
 * Full-screen gate shown while the biometric app lock is engaged. Content
 * behind it is never composed, so nothing sensitive can flash through.
 *
 * The cookie shape breathes (Cookie9Sided to Cookie6Sided) while idle and
 * speeds up while the system [promptActive]. On success ([unlocked]) it
 * turns into a primary burst with a check; after [UnlockSuccessMillis] it
 * calls [onUnlockedAnimationFinished], which is when the caller opens the gate.
 * The gate itself is untouched by any of this: the caller still decides when
 * content may compose.
 */
@Composable
fun LockScreen(
    onUnlockClick: () -> Unit,
    promptActive: Boolean = false,
    unlocked: Boolean = false,
    onUnlockedAnimationFinished: () -> Unit = {},
) {
    val haptic = LocalHapticFeedback.current

    LaunchedEffect(unlocked) {
        if (unlocked) {
            haptic.performHapticFeedback(HapticFeedbackType.Confirm)
            delay(UnlockSuccessMillis)
            onUnlockedAnimationFinished()
        }
    }

    LockContent(
        onUnlockClick = onUnlockClick,
        promptActive = promptActive,
        unlocked = unlocked,
    )
}

@Composable
internal fun LockContent(
    onUnlockClick: () -> Unit,
    promptActive: Boolean,
    unlocked: Boolean,
) {
    val idle = remember { listOf(MaterialShapes.Cookie9Sided, MaterialShapes.Cookie6Sided) }
    val success = remember { listOf(MaterialShapes.Burst) }

    val motion = MaterialTheme.motionScheme
    val container by animateColorAsState(
        targetValue = if (unlocked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.primaryContainer,
        animationSpec = motion.defaultEffectsSpec(),
        label = "lockContainer",
    )
    val content by animateColorAsState(
        targetValue = if (unlocked) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onPrimaryContainer,
        animationSpec = motion.defaultEffectsSpec(),
        label = "lockContent",
    )
    // A small swell while the system prompt is up, relaxing when it goes away.
    val swell by animateFloatAsState(
        targetValue = if (promptActive && !unlocked) 1.06f else 1f,
        animationSpec = motion.slowSpatialSpec(),
        label = "lockSwell",
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            // The only screen without a Scaffold, so nothing else applies the
            // system-bar insets for it. With edge-to-edge enforced from API 35
            // the unlock button could otherwise sit under the navigation bar on
            // a short screen or at a large font scale.
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        MorphingShape(
            polygons = if (unlocked) success else idle,
            color = container,
            holdMillis = if (promptActive) 700L else 1800L,
            rotationStepDegrees = 30f,
            fast = promptActive || unlocked,
            modifier = Modifier
                .size(168.dp)
                .graphicsLayer {
                    scaleX = swell
                    scaleY = swell
                }
                .clearAndSetSemantics { },
        ) {
            AnimatedContent(
                targetState = unlocked,
                transitionSpec = {
                    (fadeIn(motion.defaultEffectsSpec()) + scaleIn(motion.defaultSpatialSpec(), initialScale = 0.5f)) togetherWith
                        (fadeOut(motion.fastEffectsSpec()) + scaleOut(motion.fastSpatialSpec(), targetScale = 0.5f))
                },
                label = "lockGlyph",
            ) { done ->
                Icon(
                    imageVector = if (done) Icons.Filled.Check else Icons.Filled.Fingerprint,
                    contentDescription = null,
                    tint = content,
                    modifier = Modifier.size(64.dp),
                )
            }
        }
        Spacer(Modifier.height(32.dp))
        Text(
            text = stringResource(R.string.app_name),
            style = MaterialTheme.typography.headlineLargeEmphasized,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.screenHeading(),
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = stringResource(R.string.app_lock_locked_subtitle),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(32.dp))
        val height = ButtonDefaults.MediumContainerHeight
        Button(
            // Stays enabled during the success animation (no grey flash); the tap is ignored.
            onClick = { if (!unlocked) onUnlockClick() },
            shapes = ButtonDefaults.shapesFor(height),
            contentPadding = ButtonDefaults.contentPaddingFor(height, hasStartIcon = true),
            modifier = Modifier.heightIn(min = height),
        ) {
            Icon(
                imageVector = Icons.Filled.LockOpen,
                contentDescription = null,
                modifier = Modifier.size(ButtonDefaults.iconSizeFor(height)),
            )
            Spacer(Modifier.size(ButtonDefaults.iconSpacingFor(height)))
            Text(stringResource(R.string.app_lock_unlock))
        }
    }
}

@Preview(name = "Lock idle light", showBackground = true, widthDp = 360, heightDp = 720)
@Composable
private fun LockIdleLightPreview() {
    FastMaskTheme(darkTheme = false) { LockContent({}, promptActive = false, unlocked = false) }
}

@Preview(name = "Lock prompt dark", showBackground = true, widthDp = 360, heightDp = 720)
@Composable
private fun LockPromptDarkPreview() {
    FastMaskTheme(darkTheme = true) { LockContent({}, promptActive = true, unlocked = false) }
}

@Preview(name = "Lock success light", showBackground = true, widthDp = 360, heightDp = 720)
@Composable
private fun LockSuccessLightPreview() {
    FastMaskTheme(darkTheme = false) { LockContent({}, promptActive = false, unlocked = true) }
}
