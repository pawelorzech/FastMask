package com.fastmask.ui.theme

import android.os.Build
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.material3.MotionScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalInspectionMode
import com.fastmask.domain.model.Accent
import com.fastmask.ui.components.expressive.rememberMotionReduced

@Composable
fun FastMaskTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    accent: Accent = Accent.DEFAULT,
    content: @Composable () -> Unit,
) {
    val context = LocalContext.current
    val targetScheme = accent.seedScheme(darkTheme)
        ?: when {
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ->
                if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
            else -> if (darkTheme) AmberDark else AmberLight
        }
    val motionScheme = MotionScheme.expressive()
    val colorScheme = animateColorSchemeAsState(targetScheme, motionScheme)

    MaterialExpressiveTheme(
        colorScheme = colorScheme,
        motionScheme = motionScheme,
        typography = Typography,
        content = content,
    )
}

@Composable
private fun animateColorSchemeAsState(
    target: ColorScheme,
    motionScheme: MotionScheme,
): ColorScheme {
    if (LocalInspectionMode.current || rememberMotionReduced()) return target
    val spec: FiniteAnimationSpec<Color> = motionScheme.defaultEffectsSpec()

    val primary = animateColorAsState(target.primary, spec, label = "csPrimary").value
    val onPrimary = animateColorAsState(target.onPrimary, spec, label = "csOnPrimary").value
    val primaryContainer = animateColorAsState(target.primaryContainer, spec, label = "csPrimaryContainer").value
    val onPrimaryContainer = animateColorAsState(target.onPrimaryContainer, spec, label = "csOnPrimaryContainer").value
    val inversePrimary = animateColorAsState(target.inversePrimary, spec, label = "csInversePrimary").value
    val secondary = animateColorAsState(target.secondary, spec, label = "csSecondary").value
    val onSecondary = animateColorAsState(target.onSecondary, spec, label = "csOnSecondary").value
    val secondaryContainer = animateColorAsState(target.secondaryContainer, spec, label = "csSecondaryContainer").value
    val onSecondaryContainer = animateColorAsState(target.onSecondaryContainer, spec, label = "csOnSecondaryContainer").value
    val tertiary = animateColorAsState(target.tertiary, spec, label = "csTertiary").value
    val onTertiary = animateColorAsState(target.onTertiary, spec, label = "csOnTertiary").value
    val tertiaryContainer = animateColorAsState(target.tertiaryContainer, spec, label = "csTertiaryContainer").value
    val onTertiaryContainer = animateColorAsState(target.onTertiaryContainer, spec, label = "csOnTertiaryContainer").value
    val background = animateColorAsState(target.background, spec, label = "csBackground").value
    val onBackground = animateColorAsState(target.onBackground, spec, label = "csOnBackground").value
    val surface = animateColorAsState(target.surface, spec, label = "csSurface").value
    val onSurface = animateColorAsState(target.onSurface, spec, label = "csOnSurface").value
    val surfaceVariant = animateColorAsState(target.surfaceVariant, spec, label = "csSurfaceVariant").value
    val onSurfaceVariant = animateColorAsState(target.onSurfaceVariant, spec, label = "csOnSurfaceVariant").value
    val surfaceTint = animateColorAsState(target.surfaceTint, spec, label = "csSurfaceTint").value
    val inverseSurface = animateColorAsState(target.inverseSurface, spec, label = "csInverseSurface").value
    val inverseOnSurface = animateColorAsState(target.inverseOnSurface, spec, label = "csInverseOnSurface").value
    val error = animateColorAsState(target.error, spec, label = "csError").value
    val onError = animateColorAsState(target.onError, spec, label = "csOnError").value
    val errorContainer = animateColorAsState(target.errorContainer, spec, label = "csErrorContainer").value
    val onErrorContainer = animateColorAsState(target.onErrorContainer, spec, label = "csOnErrorContainer").value
    val outline = animateColorAsState(target.outline, spec, label = "csOutline").value
    val outlineVariant = animateColorAsState(target.outlineVariant, spec, label = "csOutlineVariant").value
    val surfaceBright = animateColorAsState(target.surfaceBright, spec, label = "csSurfaceBright").value
    val surfaceDim = animateColorAsState(target.surfaceDim, spec, label = "csSurfaceDim").value
    val surfaceContainerLowest = animateColorAsState(target.surfaceContainerLowest, spec, label = "csSurfaceContainerLowest").value
    val surfaceContainerLow = animateColorAsState(target.surfaceContainerLow, spec, label = "csSurfaceContainerLow").value
    val surfaceContainer = animateColorAsState(target.surfaceContainer, spec, label = "csSurfaceContainer").value
    val surfaceContainerHigh = animateColorAsState(target.surfaceContainerHigh, spec, label = "csSurfaceContainerHigh").value
    val surfaceContainerHighest = animateColorAsState(target.surfaceContainerHighest, spec, label = "csSurfaceContainerHighest").value

    val settled = primary == target.primary &&
        onPrimary == target.onPrimary &&
        primaryContainer == target.primaryContainer &&
        onPrimaryContainer == target.onPrimaryContainer &&
        inversePrimary == target.inversePrimary &&
        secondary == target.secondary &&
        onSecondary == target.onSecondary &&
        secondaryContainer == target.secondaryContainer &&
        onSecondaryContainer == target.onSecondaryContainer &&
        tertiary == target.tertiary &&
        onTertiary == target.onTertiary &&
        tertiaryContainer == target.tertiaryContainer &&
        onTertiaryContainer == target.onTertiaryContainer &&
        background == target.background &&
        onBackground == target.onBackground &&
        surface == target.surface &&
        onSurface == target.onSurface &&
        surfaceVariant == target.surfaceVariant &&
        onSurfaceVariant == target.onSurfaceVariant &&
        surfaceTint == target.surfaceTint &&
        inverseSurface == target.inverseSurface &&
        inverseOnSurface == target.inverseOnSurface &&
        error == target.error &&
        onError == target.onError &&
        errorContainer == target.errorContainer &&
        onErrorContainer == target.onErrorContainer &&
        outline == target.outline &&
        outlineVariant == target.outlineVariant &&
        surfaceBright == target.surfaceBright &&
        surfaceDim == target.surfaceDim &&
        surfaceContainerLowest == target.surfaceContainerLowest &&
        surfaceContainerLow == target.surfaceContainerLow &&
        surfaceContainer == target.surfaceContainer &&
        surfaceContainerHigh == target.surfaceContainerHigh &&
        surfaceContainerHighest == target.surfaceContainerHighest

    if (settled) return target

    return target.copy(
        primary = primary,
        onPrimary = onPrimary,
        primaryContainer = primaryContainer,
        onPrimaryContainer = onPrimaryContainer,
        inversePrimary = inversePrimary,
        secondary = secondary,
        onSecondary = onSecondary,
        secondaryContainer = secondaryContainer,
        onSecondaryContainer = onSecondaryContainer,
        tertiary = tertiary,
        onTertiary = onTertiary,
        tertiaryContainer = tertiaryContainer,
        onTertiaryContainer = onTertiaryContainer,
        background = background,
        onBackground = onBackground,
        surface = surface,
        onSurface = onSurface,
        surfaceVariant = surfaceVariant,
        onSurfaceVariant = onSurfaceVariant,
        surfaceTint = surfaceTint,
        inverseSurface = inverseSurface,
        inverseOnSurface = inverseOnSurface,
        error = error,
        onError = onError,
        errorContainer = errorContainer,
        onErrorContainer = onErrorContainer,
        outline = outline,
        outlineVariant = outlineVariant,
        surfaceBright = surfaceBright,
        surfaceDim = surfaceDim,
        surfaceContainerLowest = surfaceContainerLowest,
        surfaceContainerLow = surfaceContainerLow,
        surfaceContainer = surfaceContainer,
        surfaceContainerHigh = surfaceContainerHigh,
        surfaceContainerHighest = surfaceContainerHighest,
    )
}
