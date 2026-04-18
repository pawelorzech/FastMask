package com.fastmask.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider

private val LightColorScheme = lightColorScheme(
    primary = AccentAmber,
    onPrimary = OnAccent,
    primaryContainer = LightActiveBg,
    onPrimaryContainer = LightActiveInk,
    secondary = LightInkSoft,
    onSecondary = LightSurface,
    tertiary = LightInkMuted,
    onTertiary = LightSurface,
    error = LightArchivedInk,
    onError = OnAccent,
    errorContainer = LightArchivedBg,
    onErrorContainer = LightArchivedInk,
    background = LightBg,
    onBackground = LightInk,
    surface = LightSurface,
    onSurface = LightInk,
    surfaceVariant = LightSurfaceAlt,
    onSurfaceVariant = LightInkSoft,
    outline = LightLine,
    outlineVariant = LightLineStrong,
    surfaceContainerLowest = LightSurface,
    surfaceContainerLow = LightSurface,
    surfaceContainer = LightSurfaceAlt,
    surfaceContainerHigh = LightSurfaceAlt,
    surfaceContainerHighest = LightChip,
)

private val DarkColorScheme = darkColorScheme(
    primary = AccentAmber,
    onPrimary = OnAccent,
    primaryContainer = DarkActiveBg,
    onPrimaryContainer = DarkActiveInk,
    secondary = DarkInkSoft,
    onSecondary = DarkSurface,
    tertiary = DarkInkMuted,
    onTertiary = DarkSurface,
    error = DarkArchivedInk,
    onError = OnAccent,
    errorContainer = DarkArchivedBg,
    onErrorContainer = DarkArchivedInk,
    background = DarkBg,
    onBackground = DarkInk,
    surface = DarkSurface,
    onSurface = DarkInk,
    surfaceVariant = DarkSurfaceAlt,
    onSurfaceVariant = DarkInkSoft,
    outline = DarkLine,
    outlineVariant = DarkLineStrong,
    surfaceContainerLowest = DarkBg,
    surfaceContainerLow = DarkSurface,
    surfaceContainer = DarkSurfaceAlt,
    surfaceContainerHigh = DarkSurfaceAlt,
    surfaceContainerHighest = DarkChip,
)

@Composable
fun FastMaskTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme
    val extras = if (darkTheme) DarkExtras else LightExtras

    CompositionLocalProvider(
        LocalStatusColors provides extras.status,
        LocalFastMaskExtras provides extras,
    ) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = Typography,
            content = content,
        )
    }
}
