package com.fastmask.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.material3.MotionScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import com.fastmask.domain.model.Accent

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

    MaterialExpressiveTheme(
        colorScheme = colorScheme,
        motionScheme = MotionScheme.expressive(),
        typography = Typography,
        content = content,
    )
}
