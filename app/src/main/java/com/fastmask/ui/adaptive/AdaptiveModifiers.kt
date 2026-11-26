@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)

package com.fastmask.ui.adaptive

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PlainTooltip
import androidx.compose.material3.Text
import androidx.compose.material3.TooltipAnchorPosition
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.rememberTooltipState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isAltPressed
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isMetaPressed
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.isSecondaryPressed
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp

/**
 * Adds pointer hover feedback, hand cursor, and a visible Material 3 keyboard/D-pad focus ring
 * animated via `MaterialTheme.motionScheme`.
 */
fun Modifier.fastMaskFocusAndHover(
    interactionSource: MutableInteractionSource,
    shape: Shape,
    showFocusRing: Boolean = true,
): Modifier = composed {
    val motion = MaterialTheme.motionScheme
    val hovered by interactionSource.collectIsHoveredAsState()
    val focused by interactionSource.collectIsFocusedAsState()
    val hoverOverlay by animateColorAsState(
        targetValue = if (hovered) MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f) else Color.Transparent,
        animationSpec = motion.fastEffectsSpec(),
        label = "fastmask-hover-overlay",
    )
    val focusRingColor by animateColorAsState(
        targetValue = if (showFocusRing && focused) MaterialTheme.colorScheme.primary else Color.Transparent,
        animationSpec = motion.fastEffectsSpec(),
        label = "fastmask-focus-ring",
    )
    this
        .hoverable(interactionSource)
        .pointerHoverIcon(PointerIcon.Hand)
        .background(hoverOverlay, shape)
        .then(
            if (showFocusRing && focused) Modifier.border(2.dp, focusRingColor, shape)
            else Modifier,
        )
}

/**
 * Detects secondary (right-click) pointer presses without interfering with primary clicks,
 * touch taps, or drag-and-drop gestures.
 */
fun Modifier.onSecondaryClick(onRightClick: () -> Unit): Modifier = pointerInput(onRightClick) {
    awaitPointerEventScope {
        while (true) {
            val event = awaitPointerEvent()
            if (event.type == PointerEventType.Press && event.buttons.isSecondaryPressed) {
                event.changes.forEach { it.consume() }
                onRightClick()
            }
        }
    }
}

/**
 * Hardware keyboard shortcuts for the masks list and two-pane workspace.
 *
 * `Ctrl+N`, `Ctrl+F` and `Esc` run in the preview phase so they work from anywhere.
 * Plain `/`, `Ctrl+C`, `Del`, `↑` and `↓` run in the bubbling `onKeyEvent` phase so
 * focused text fields keep normal typing, copy, delete and cursor movement.
 */
fun Modifier.fastMaskKeyShortcuts(
    onNewMask: (() -> Unit)? = null,
    onSearch: (() -> Unit)? = null,
    onCopySelected: (() -> Boolean)? = null,
    onArchiveSelected: (() -> Boolean)? = null,
    onEscape: (() -> Boolean)? = null,
    onMoveSelection: ((delta: Int) -> Boolean)? = null,
): Modifier = this
    .onPreviewKeyEvent { event ->
        if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
        val ctrlOnly = event.isCtrlPressed && !event.isAltPressed && !event.isMetaPressed && !event.isShiftPressed
        when {
            ctrlOnly && event.key == Key.N && onNewMask != null -> {
                onNewMask()
                true
            }
            ctrlOnly && event.key == Key.F && onSearch != null -> {
                onSearch()
                true
            }
            event.key == Key.Escape && onEscape != null -> onEscape()
            else -> false
        }
    }
    .onKeyEvent { event ->
        if (event.type != KeyEventType.KeyDown) return@onKeyEvent false
        val plain = !event.isCtrlPressed && !event.isAltPressed && !event.isMetaPressed && !event.isShiftPressed
        val ctrlOnly = event.isCtrlPressed && !event.isAltPressed && !event.isMetaPressed && !event.isShiftPressed
        when {
            plain && event.key == Key.Slash && onSearch != null -> {
                onSearch()
                true
            }
            ctrlOnly && event.key == Key.C && onCopySelected != null -> onCopySelected()
            plain && (event.key == Key.Delete || event.key == Key.Backspace) && onArchiveSelected != null ->
                onArchiveSelected()
            plain && event.key == Key.DirectionDown && onMoveSelection != null -> onMoveSelection(1)
            plain && event.key == Key.DirectionUp && onMoveSelection != null -> onMoveSelection(-1)
            else -> false
        }
    }

/**
 * Wraps an icon-only button with a Material 3 [PlainTooltip] for mouse hover and long-press
 * without altering the button's accessible semantics.
 */
@Composable
fun ActionTooltip(
    label: String,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    TooltipBox(
        positionProvider = TooltipDefaults.rememberTooltipPositionProvider(TooltipAnchorPosition.Above),
        tooltip = { PlainTooltip { Text(label) } },
        state = rememberTooltipState(),
        modifier = modifier,
        content = content,
    )
}
