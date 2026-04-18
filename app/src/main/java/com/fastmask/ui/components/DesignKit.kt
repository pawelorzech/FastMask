package com.fastmask.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.fastmask.domain.model.EmailState
import com.fastmask.ui.theme.FastMaskExtras
import com.fastmask.ui.theme.MonoLabelStyle
import com.fastmask.ui.theme.MonoSmallStyle

// ============================================================
// Mono labels — uppercase tiny markers used throughout the design
// ============================================================
@Composable
fun MonoLabel(
    text: String,
    modifier: Modifier = Modifier,
    color: Color? = null,
) {
    val resolved = color ?: FastMaskExtras.current.inkMuted
    Text(
        text = text.uppercase(),
        style = MonoLabelStyle,
        color = resolved,
        modifier = modifier,
    )
}

@Composable
fun MonoEyebrow(
    text: String,
    modifier: Modifier = Modifier,
    color: Color? = null,
) {
    val resolved = color ?: FastMaskExtras.current.inkMuted
    Text(
        text = text.uppercase(),
        style = MonoSmallStyle,
        color = resolved,
        modifier = modifier,
    )
}

// ============================================================
// State dot — small filled circle with halo ring (list cards)
// ============================================================
@Composable
fun StateDot(
    state: EmailState,
    modifier: Modifier = Modifier,
    size: Dp = 10.dp,
) {
    val pair = pairFor(state)
    Box(
        modifier = modifier
            .size(size + 6.dp)
            .clip(CircleShape)
            .background(pair.container),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .size(size)
                .clip(CircleShape)
                .background(pair.content),
        )
    }
}

// ============================================================
// State pill — pill with content-color dot + lowercase mono label
// ============================================================
@Composable
fun StatePill(
    state: EmailState,
    label: String,
    modifier: Modifier = Modifier,
) {
    val pair = pairFor(state)
    Surface(
        shape = RoundedCornerShape(100.dp),
        color = pair.container,
        contentColor = pair.content,
        modifier = modifier,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(start = 8.dp, end = 10.dp, top = 4.dp, bottom = 4.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(6.dp)
                    .clip(CircleShape)
                    .background(pair.content),
            )
            Spacer(Modifier.width(6.dp))
            Text(
                text = label.lowercase(),
                style = MonoLabelStyle.copy(fontWeight = FontWeight.Medium),
                color = pair.content,
            )
        }
    }
}

@Composable
private fun pairFor(state: EmailState) = with(FastMaskExtras.current.status) {
    when (state) {
        EmailState.ENABLED -> enabled
        EmailState.DISABLED -> disabled
        EmailState.DELETED -> deleted
        EmailState.PENDING -> pending
    }
}

// ============================================================
// Pill back / circular icon button (used for top bar)
// ============================================================
@Composable
fun PillIconButton(
    onClick: () -> Unit,
    contentDescription: String,
    modifier: Modifier = Modifier,
    tint: Color? = null,
    content: @Composable () -> Unit,
) {
    val haptic = LocalHapticFeedback.current
    val border = MaterialTheme.colorScheme.outline
    val tone = tint ?: MaterialTheme.colorScheme.onBackground
    Box(
        modifier = modifier
            .size(40.dp)
            .clip(CircleShape)
            .border(BorderStroke(1.dp, border), CircleShape)
            .clickable(role = Role.Button, onClickLabel = contentDescription) {
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                onClick()
            },
        contentAlignment = Alignment.Center,
    ) {
        CompositionLocalProvider(LocalContentColor provides tone) {
            content()
        }
    }
}

// ============================================================
// Primary pill button (accent fill) and variants
// ============================================================
enum class PillButtonVariant { Primary, Secondary, Ghost, Danger, Active }

@Composable
fun PillButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    variant: PillButtonVariant = PillButtonVariant.Primary,
    enabled: Boolean = true,
    leading: (@Composable () -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
    fullWidth: Boolean = false,
) {
    val extras = FastMaskExtras.current
    val haptic = LocalHapticFeedback.current

    val (bg, fg, borderColor) = when (variant) {
        PillButtonVariant.Primary -> Triple(extras.accent, extras.onAccent, Color.Transparent)
        PillButtonVariant.Secondary -> Triple(extras.surfaceAlt, MaterialTheme.colorScheme.onBackground, MaterialTheme.colorScheme.outline)
        PillButtonVariant.Ghost -> Triple(Color.Transparent, MaterialTheme.colorScheme.onBackground, MaterialTheme.colorScheme.outline)
        PillButtonVariant.Danger -> Triple(Color.Transparent, extras.status.deleted.content, extras.status.deleted.container)
        PillButtonVariant.Active -> Triple(extras.status.enabled.container, extras.status.enabled.content, Color.Transparent)
    }

    val shape = RoundedCornerShape(14.dp)
    val base = Modifier
        .then(if (fullWidth) Modifier.fillMaxWidth() else Modifier)
        .clip(shape)
        .background(bg, shape)
        .border(BorderStroke(1.dp, borderColor), shape)
        .clickable(enabled = enabled) {
            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
            onClick()
        }
        .padding(horizontal = 20.dp, vertical = 14.dp)

    Row(
        modifier = modifier.then(base),
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        leading?.invoke()
        Text(
            text = text,
            color = if (enabled) fg else fg.copy(alpha = 0.5f),
            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
        )
        trailing?.invoke()
    }
}

// ============================================================
// Card surface used everywhere — line border + warm surface
// ============================================================
@Composable
fun DesignCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    val border = MaterialTheme.colorScheme.outline
    val shape = RoundedCornerShape(16.dp)
    val haptic = LocalHapticFeedback.current
    Box(
        modifier = modifier
            .clip(shape)
            .background(MaterialTheme.colorScheme.surface, shape)
            .border(BorderStroke(1.dp, border), shape)
            .then(
                if (onClick != null) {
                    Modifier.clickable {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        onClick()
                    }
                } else Modifier,
            ),
    ) {
        content()
    }
}

@Composable
fun DashedDesignCard(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    // Approximation of dashed border using lineStrong color on the warm surface.
    val border = FastMaskExtras.current.lineStrong
    val shape = RoundedCornerShape(14.dp)
    Box(
        modifier = modifier
            .clip(shape)
            .background(MaterialTheme.colorScheme.surface, shape)
            .border(BorderStroke(1.dp, border), shape),
    ) {
        content()
    }
}

@Composable
fun HairlineDivider(modifier: Modifier = Modifier) {
    Box(
        modifier
            .fillMaxWidth()
            .height(1.dp)
            .background(MaterialTheme.colorScheme.outline),
    )
}
