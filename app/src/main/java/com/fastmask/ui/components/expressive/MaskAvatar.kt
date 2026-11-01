package com.fastmask.ui.components.expressive

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.graphics.shapes.RoundedPolygon
import com.fastmask.domain.model.EmailState
import com.fastmask.ui.theme.FastMaskTheme

/** The shapes [MaskAvatar] picks from, in a fixed order so the pick is stable across releases. */
internal fun maskAvatarPolygons(): List<RoundedPolygon> = listOf(
    MaterialShapes.Cookie4Sided,
    MaterialShapes.Clover4Leaf,
    MaterialShapes.Cookie9Sided,
    MaterialShapes.Pentagon,
    MaterialShapes.Gem,
    MaterialShapes.Flower,
    MaterialShapes.Cookie6Sided,
    MaterialShapes.Triangle,
)

/** Deterministic index into [maskAvatarPolygons] for [seedText]. */
internal fun maskAvatarShapeIndex(seedText: String, shapeCount: Int): Int =
    Math.floorMod(seedText.hashCode(), shapeCount)

/** First letter of [seedText] (uppercase), or "?" when blank. */
internal fun maskAvatarInitial(seedText: String): String =
    seedText.trim().firstOrNull { it.isLetterOrDigit() }?.uppercaseChar()?.toString() ?: "?"

/**
 * Avatar for a mask: the first letter of [seedText] (a domain or e-mail) on a
 * Material Expressive shape chosen deterministically from a hash of
 * [seedText], so the same site always gets the same shape. Colors come from
 * the state's status pair. Decorative: it has no semantics of its own, pair
 * it with a [StateChip] or text for the state.
 */
@Composable
fun MaskAvatar(
    seedText: String,
    state: EmailState,
    modifier: Modifier = Modifier,
    size: Dp = 40.dp,
) {
    val polygons = remember { maskAvatarPolygons() }
    val polygon = polygons[maskAvatarShapeIndex(seedText, polygons.size)]
    val shape = polygon.toShape()
    val pair = statusPairFor(state)
    Box(
        modifier = modifier
            .size(size)
            .clip(shape)
            .background(pair.container)
            .clearAndSetSemantics { },
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = maskAvatarInitial(seedText),
            color = pair.content,
            style = MaterialTheme.typography.titleMediumEmphasized,
            fontSize = (size.value * 0.4f).sp,
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun MaskAvatarPreview() {
    FastMaskTheme {
        Row(Modifier.padding(8.dp)) {
            listOf("github.com", "amazon.pl", "news.ycombinator.com", "x.com", "nib.app").forEach {
                MaskAvatar(it, EmailState.ENABLED, Modifier.padding(4.dp))
            }
        }
    }
}
