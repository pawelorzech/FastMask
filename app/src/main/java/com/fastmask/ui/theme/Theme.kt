package com.fastmask.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MotionScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import com.fastmask.domain.model.Accent

/** Fixed seed scheme for [accent], or null for [Accent.DYNAMIC]. */
internal fun Accent.seedScheme(dark: Boolean): ColorScheme? = when (this) {
    Accent.DYNAMIC -> null
    Accent.AMBER -> if (dark) AmberDark else AmberLight
    Accent.INK -> if (dark) InkDark else InkLight
    Accent.SAGE -> if (dark) SageDark else SageLight
    Accent.PLUM -> if (dark) PlumDark else PlumLight
    Accent.COBALT -> if (dark) CobaltDark else CobaltLight
}

/**
 * Swatch color for the accent picker: the seed scheme's `primary`. For
 * [Accent.DYNAMIC] it is the *current* `MaterialTheme.colorScheme.primary`
 * (wallpaper-derived on API 31+), hence @Composable.
 */
@Composable
fun Accent.swatchColor(dark: Boolean): Color =
    seedScheme(dark)?.primary ?: MaterialTheme.colorScheme.primary

@Composable
fun FastMaskTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    accent: Accent = Accent.DEFAULT,
    content: @Composable () -> Unit,
) {
    val context = LocalContext.current
    val colorScheme = accent.seedScheme(darkTheme)
        ?: when {
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ->
                if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
            else -> if (darkTheme) AmberDark else AmberLight
        }
    val status = remember(colorScheme) { colorScheme.toStatusColors() }
    val extras = remember(colorScheme, status) { colorScheme.toExtras(status) }

    CompositionLocalProvider(
        LocalStatusColors provides status,
        LocalFastMaskExtras provides extras,
    ) {
        MaterialExpressiveTheme(
            colorScheme = colorScheme,
            motionScheme = MotionScheme.expressive(),
            typography = Typography,
            content = content,
        )
    }
}
