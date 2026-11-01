package com.fastmask.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color

/** Container/content colors of one mask state, read from the active [androidx.compose.material3.ColorScheme]. */
@Immutable
data class StatusColorPair(
    val container: Color,
    val content: Color,
)
