package com.fastmask.ui.components.expressive

import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Matrix
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.graphics.shapes.Morph

/** Clips to the [morph] at [progress] (read lazily, so animation does not recompose). */
internal class MorphShape(
    private val morph: Morph,
    private val progress: () -> Float,
) : Shape {
    private val path = Path()
    private val matrix = Matrix()

    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
        morph.toScaledPath(progress(), size, path, matrix)
        return Outline.Generic(path)
    }
}
