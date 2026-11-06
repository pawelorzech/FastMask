package com.fastmask.ui.components.expressive

import androidx.compose.material3.toPath
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Matrix
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.graphics.shapes.Morph

/**
 * Writes this morph at [progress] into [path], scaled to [size].
 *
 * MaterialShapes polygons are normalized to a 1x1 box, so every consumer
 * (clip shape, draw modifier, [MorphingShape]) needs the same
 * rewind / toPath / scale step; [matrix] is scratch space the caller keeps so
 * nothing is allocated per frame.
 */
internal fun Morph.toScaledPath(progress: Float, size: Size, path: Path, matrix: Matrix) {
    path.rewind()
    toPath(progress = progress, path = path)
    matrix.reset()
    matrix.scale(size.width, size.height)
    path.transform(matrix)
}

/**
 * Draws [morph] at [progress] filling the node's bounds by calling [paint] with
 * the scaled outline path.
 *
 * Both [morph] and [progress] are read inside the draw phase, so animating them
 * invalidates drawing only, never composition or layout. (This is drawn, not
 * used as a clip `Shape`: a clip outline built from changing state is not
 * guaranteed to be re-resolved when that state changes.) Shared by the color
 * swatches.
 */
internal fun Modifier.drawMorph(
    morph: () -> Morph,
    progress: () -> Float,
    paint: DrawScope.(Path) -> Unit,
): Modifier = drawWithCache {
    val path = Path()
    val matrix = Matrix()
    onDrawBehind {
        morph().toScaledPath(progress(), size, path, matrix)
        paint(path)
    }
}
