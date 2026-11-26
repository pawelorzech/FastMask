package com.fastmask.ui.adaptive

import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.AlertDialogDefaults
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

/**
 * Dialog host that positions its card strictly away from any separating vertical or horizontal
 * fold hinge. On flat screens and phones it centers the card with standard Material 3 width caps.
 */
@Composable
fun HingeAwareDialog(
    onDismissRequest: () -> Unit,
    posture: FastMaskPosture = currentFastMaskAdaptiveInfo().posture,
    content: @Composable () -> Unit,
) {
    val density = LocalDensity.current
    var windowLeftPx by remember { mutableFloatStateOf(0f) }
    var windowTopPx by remember { mutableFloatStateOf(0f) }

    Dialog(
        onDismissRequest = onDismissRequest,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .onGloballyPositioned { coords ->
                    val b = coords.boundsInWindow()
                    windowLeftPx = b.left
                    windowTopPx = b.top
                }
                // The dialog window spans the display so the platform never sees an outside tap;
                // dismiss on taps beside the card while the card itself consumes taps.
                .pointerInput(onDismissRequest) { detectTapGestures { onDismissRequest() } },
        ) {
            val vHinge = posture.separatingVerticalHinges.firstOrNull()
            val hHinge = if (posture.isTabletop) {
                posture.separatingHorizontalHinges.firstOrNull()
                    ?: posture.allHorizontalHinges.firstOrNull()
            } else {
                null
            }
            when {
                vHinge != null -> {
                    val hingeRightDp = with(density) { (vHinge.right - windowLeftPx).coerceAtLeast(0f).toDp() }
                    val startOffset = if (hingeRightDp > 0.dp && hingeRightDp < maxWidth - 120.dp) {
                        hingeRightDp + 12.dp
                    } else {
                        maxWidth / 2 + 12.dp
                    }
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(start = startOffset, end = 16.dp, top = 16.dp, bottom = 16.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Box(
                            Modifier
                                .widthIn(min = 280.dp, max = 480.dp)
                                .consumeTaps()
                                .testTag("hinge_aware_dialog_box"),
                        ) {
                            content()
                        }
                    }
                }
                hHinge != null -> {
                    val hingeBottomDp = with(density) { (hHinge.bottom - windowTopPx).coerceAtLeast(0f).toDp() }
                    val topOffset = if (hingeBottomDp > 0.dp && hingeBottomDp < maxHeight - 120.dp) {
                        hingeBottomDp + 12.dp
                    } else {
                        maxHeight / 2 + 12.dp
                    }
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(top = topOffset, start = 24.dp, end = 24.dp, bottom = 16.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Box(
                            Modifier
                                .widthIn(min = 280.dp, max = 520.dp)
                                .consumeTaps()
                                .testTag("hinge_aware_dialog_box"),
                        ) {
                            content()
                        }
                    }
                }
                else -> {
                    Box(
                        modifier = Modifier.fillMaxSize().padding(24.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Box(
                            Modifier
                                .widthIn(min = 280.dp, max = 560.dp)
                                .consumeTaps()
                                .testTag("hinge_aware_dialog_box"),
                        ) {
                            content()
                        }
                    }
                }
            }
        }
    }
}

/**
 * Material 3 styled alert dialog rendered inside [HingeAwareDialog] so every confirmation and
 * picker dialog avoids foldable creases without losing Material 3 dialog visuals.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun HingeAwareAlertDialog(
    onDismissRequest: () -> Unit,
    confirmButton: @Composable () -> Unit,
    dismissButton: (@Composable () -> Unit)? = null,
    icon: (@Composable () -> Unit)? = null,
    title: (@Composable () -> Unit)? = null,
    text: (@Composable () -> Unit)? = null,
    posture: FastMaskPosture = currentFastMaskAdaptiveInfo().posture,
) {
    HingeAwareDialog(onDismissRequest = onDismissRequest, posture = posture) {
        Surface(
            shape = AlertDialogDefaults.shape,
            color = AlertDialogDefaults.containerColor,
            tonalElevation = AlertDialogDefaults.TonalElevation,
            shadowElevation = 6.dp,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(modifier = Modifier.padding(24.dp)) {
                if (icon != null) {
                    CompositionLocalProvider(LocalContentColor provides AlertDialogDefaults.iconContentColor) {
                        Box(
                            modifier = Modifier
                                .padding(bottom = 16.dp)
                                .align(Alignment.CenterHorizontally),
                        ) {
                            icon()
                        }
                    }
                }
                if (title != null) {
                    CompositionLocalProvider(LocalContentColor provides AlertDialogDefaults.titleContentColor) {
                        ProvideTextStyle(MaterialTheme.typography.headlineSmall) {
                            Box(
                                modifier = Modifier
                                    .padding(bottom = 16.dp)
                                    .align(if (icon != null) Alignment.CenterHorizontally else Alignment.Start),
                            ) {
                                title()
                            }
                        }
                    }
                }
                if (text != null) {
                    CompositionLocalProvider(LocalContentColor provides AlertDialogDefaults.textContentColor) {
                        ProvideTextStyle(MaterialTheme.typography.bodyMedium) {
                            Box(
                                modifier = Modifier
                                    .weight(weight = 1f, fill = false)
                                    .padding(bottom = 24.dp)
                                    .align(Alignment.Start),
                            ) {
                                text()
                            }
                        }
                    }
                }
                Box(modifier = Modifier.align(Alignment.End)) {
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        dismissButton?.invoke()
                        confirmButton()
                    }
                }
            }
        }
    }
}

/** Swallows taps on the dialog card so they never reach the dismiss-on-tap backdrop behind it. */
private fun Modifier.consumeTaps(): Modifier = pointerInput(Unit) { detectTapGestures { } }
