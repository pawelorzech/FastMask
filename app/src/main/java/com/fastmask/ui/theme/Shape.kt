package com.fastmask.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

/**
 * FastMask's Material 3 shape scale.
 *
 * The contrast between compact controls and generous content containers is
 * the quiet version of Material 3 Expressive: recognisable hierarchy without
 * making a privacy utility feel playful or noisy.
 */
val FastMaskShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(20.dp),
    large = RoundedCornerShape(28.dp),
    extraLarge = RoundedCornerShape(36.dp),
)
