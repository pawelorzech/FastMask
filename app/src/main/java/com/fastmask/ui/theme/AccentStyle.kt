package com.fastmask.ui.theme

import androidx.annotation.StringRes
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialShapes
import androidx.graphics.shapes.RoundedPolygon
import com.fastmask.R
import com.fastmask.domain.model.Accent

internal class AccentStyle(
    val light: ColorScheme?,
    val dark: ColorScheme?,
    val shape: RoundedPolygon,
    @param:StringRes val nameRes: Int,
)

internal val Accent.style: AccentStyle
    get() = when (this) {
        Accent.DYNAMIC -> AccentStyle(null, null, MaterialShapes.Circle, R.string.accent_dynamic)
        Accent.AMBER -> AccentStyle(AmberLight, AmberDark, MaterialShapes.Cookie4Sided, R.string.accent_amber)
        Accent.INK -> AccentStyle(InkLight, InkDark, MaterialShapes.Cookie9Sided, R.string.accent_ink)
        Accent.SAGE -> AccentStyle(SageLight, SageDark, MaterialShapes.Clover4Leaf, R.string.accent_sage)
        Accent.PLUM -> AccentStyle(PlumLight, PlumDark, MaterialShapes.Flower, R.string.accent_plum)
        Accent.COBALT -> AccentStyle(CobaltLight, CobaltDark, MaterialShapes.Pentagon, R.string.accent_cobalt)
    }

internal fun Accent.seedScheme(dark: Boolean): ColorScheme? = if (dark) style.dark else style.light
