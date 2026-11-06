package com.fastmask.ui.list

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.getTextLayoutResult
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.text
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.fastmask.R
import com.fastmask.domain.model.EmailState
import com.fastmask.domain.model.MaskedEmail
import com.fastmask.ui.components.expressive.MaskAvatar
import com.fastmask.ui.components.expressive.SegmentedRow
import com.fastmask.ui.components.expressive.StateChip
import com.fastmask.ui.components.expressive.segmentedShape
import com.fastmask.ui.components.labelRes
import com.fastmask.ui.navigation.maskContainer
import com.fastmask.ui.util.RelativeTime
import com.fastmask.ui.util.RelativeTimeBuckets

/** Font scale from which a mask row drops its trailing time label / state chip. */
private const val CompactRowFontScale = 1.3f

@Composable
internal fun MaskRow(
    email: MaskedEmail,
    index: Int,
    count: Int,
    nowSec: Long,
    highlighted: Boolean,
    onOpen: (MaskedEmail) -> Unit,
    onCopy: (MaskedEmail) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val copyLabel = stringResource(R.string.list_copy_action)
    val stateLabel = stringResource(email.state.labelRes)
    // Key the formatted string on the coarse relative bucket rather than raw
    // `nowSec` so a refresh that advances `nowSec` by a few seconds does not
    // re-run ICU formatting for every row whose bucket ("3d ago") did not change.
    val timeBucket = remember(email.lastActivityAt, nowSec) {
        RelativeTimeBuckets.of(email.lastActivityAt?.epochSecond, nowSec)
    }
    val timeLabel = remember(timeBucket, context) {
        RelativeTime.format(context, timeBucket)
    }
    val mutedColor = MaterialTheme.colorScheme.onSurfaceVariant
    // Wraps only before the "@" (see addressWrapText); semantics keep the plain address.
    val headline = remember(email.email, mutedColor) {
        val display = addressWrapText(email.email)
        val at = addressWrapDomainStart(display)
        buildAnnotatedString {
            if (at < 0) {
                append(display)
            } else {
                append(display.substring(0, at))
                withStyle(SpanStyle(color = mutedColor)) { append(display.substring(at)) }
            }
        }
    }
    val domain = email.siteDomain
    val note = email.description?.takeIf { it.isNotBlank() }
    // At large font scales the timestamp / state chip beside the address left it a few
    // characters ("sha…"). Past this threshold they move under the address instead.
    val compact = LocalDensity.current.fontScale >= CompactRowFontScale
    val supporting = listOfNotNull(
        note,
        domain,
        stateLabel.takeIf { compact && email.state != EmailState.ENABLED },
        timeLabel.takeIf { compact },
    ).joinToString(" · ")

    // The freshly created mask flashes a primaryContainer wash that fades out.
    val highlightAlpha by animateFloatAsState(
        targetValue = if (highlighted) 0.55f else 0f,
        animationSpec = MaterialTheme.motionScheme.slowEffectsSpec(),
        label = "justCreated",
    )
    val highlightColor = MaterialTheme.colorScheme.primaryContainer
    val restingShape = segmentedShape(index, count)

    SegmentedRow(
        index = index,
        count = count,
        onClick = { onOpen(email) },
        modifier = modifier
            .maskContainer(email.id, shape = restingShape)
            .drawWithContent {
                drawContent()
                if (highlightAlpha > 0.001f) {
                    drawOutline(
                        outline = restingShape.createOutline(size, layoutDirection, this),
                        color = highlightColor,
                        alpha = highlightAlpha,
                    )
                }
            }
            // The avatar's color is the only visual cue for the state; say it.
            .semantics { stateDescription = stateLabel },
        leading = {
            MaskAvatar(
                seedText = email.avatarSeed,
                state = email.state,
                size = 40.dp,
            )
        },
        supporting = if (supporting.isNotEmpty()) {
            {
                Text(
                    text = supporting,
                    maxLines = if (compact) 3 else 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        } else {
            null
        },
        trailing = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                // Compact rows carry both in the supporting line instead.
                if (!compact && email.state == EmailState.ENABLED) {
                    Text(
                        text = timeLabel,
                        style = MaterialTheme.typography.labelMedium,
                        color = mutedColor,
                    )
                } else if (!compact) {
                    // The row already announces its state; the chip is the
                    // sighted user's non-colour cue only.
                    Box(Modifier.clearAndSetSemantics { }) { StateChip(email.state) }
                }
                // Quick-copy: the most common action, without opening the
                // detail screen. Stock IconButton: 48dp target, press morph.
                IconButton(onClick = { onCopy(email) }) {
                    Icon(
                        imageVector = Icons.Filled.ContentCopy,
                        contentDescription = copyLabel,
                    )
                }
            }
        },
        headline = {
            // The address is the row's identity: wrap it rather than cut off the domain.
            // Semantics carry the plain address (the display text has invisible wrap hints)
            // and still expose the real layout, so accessibility and tests see what is drawn.
            var addressLayout by remember { mutableStateOf<TextLayoutResult?>(null) }
            Text(
                text = headline,
                maxLines = if (compact) 4 else 2,
                overflow = TextOverflow.Ellipsis,
                onTextLayout = { addressLayout = it },
                modifier = Modifier.clearAndSetSemantics {
                    text = AnnotatedString(email.email)
                    getTextLayoutResult { results ->
                        addressLayout?.let { results.add(it); true } ?: false
                    }
                },
            )
        },
    )
}
