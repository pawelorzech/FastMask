package com.fastmask.ui.settings

import androidx.compose.material3.toPath
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.graphics.Matrix
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.graphics.shapes.Morph

/**
 * Draws [morph] at [progress] filling the node's bounds by calling [paint] with
 * the scaled outline path.
 *
 * Both [morph] and [progress] are read inside the draw phase, so animating them
 * invalidates drawing only, never composition or layout. (This is drawn, not
 * used as a clip `Shape`: a clip outline built from changing state is not
 * guaranteed to be re-resolved when that state changes.) Shared by the color
 * swatches and the Pro hero.
 */
internal fun Modifier.drawMorph(
    morph: () -> Morph,
    progress: () -> Float,
    paint: DrawScope.(Path) -> Unit,
): Modifier = drawWithCache {
    val path = Path()
    val matrix = Matrix()
    onDrawBehind {
        path.rewind()
        morph().toPath(progress = progress(), path = path)
        // MaterialShapes polygons are normalized to a 1x1 box.
        matrix.reset()
        matrix.scale(size.width, size.height)
        path.transform(matrix)
        paint(path)
    }
}
