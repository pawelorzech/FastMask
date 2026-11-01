package com.fastmask.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.googlefonts.Font
import androidx.compose.ui.text.googlefonts.GoogleFont
import androidx.compose.ui.unit.sp

private val provider = GoogleFont.Provider(
    providerAuthority = "com.google.android.gms.fonts",
    providerPackage = "com.google.android.gms",
    certificates = com.fastmask.R.array.com_google_android_gms_fonts_certs,
)

private val GoogleSansFlexGF = GoogleFont("Google Sans Flex")

/**
 * The single app typeface, fetched through the downloadable-fonts provider.
 * If GMS lacks the family the platform falls back to the default sans automatically.
 */
val GoogleSansFlex = FontFamily(
    Font(googleFont = GoogleSansFlexGF, fontProvider = provider, weight = FontWeight.Normal),
    Font(googleFont = GoogleSansFlexGF, fontProvider = provider, weight = FontWeight.Medium),
    Font(googleFont = GoogleSansFlexGF, fontProvider = provider, weight = FontWeight.SemiBold),
    Font(googleFont = GoogleSansFlexGF, fontProvider = provider, weight = FontWeight.Bold),
)

private fun style(
    size: Int,
    line: Int,
    weight: FontWeight,
    tracking: Float = 0f,
) = TextStyle(
    fontFamily = GoogleSansFlex,
    fontWeight = weight,
    fontSize = size.sp,
    lineHeight = line.sp,
    letterSpacing = tracking.sp,
)

private val W400 = FontWeight.Normal
private val W500 = FontWeight.Medium
private val W600 = FontWeight.SemiBold
private val W700 = FontWeight.Bold

/**
 * M3 Expressive type scale. Each role has a baseline and an `…Emphasized`
 * variant that is one weight step heavier (display/headline jump to 600).
 */
val Typography = Typography(
    displayLarge = style(57, 64, W400, -0.25f),
    displayMedium = style(45, 52, W400),
    displaySmall = style(36, 44, W400),
    headlineLarge = style(32, 40, W400),
    headlineMedium = style(28, 36, W400),
    headlineSmall = style(24, 32, W400),
    titleLarge = style(22, 28, W400),
    titleMedium = style(16, 24, W500, 0.15f),
    titleSmall = style(14, 20, W500, 0.1f),
    bodyLarge = style(16, 24, W400, 0.5f),
    bodyMedium = style(14, 20, W400, 0.25f),
    bodySmall = style(12, 16, W400, 0.4f),
    labelLarge = style(14, 20, W500, 0.1f),
    labelMedium = style(12, 16, W500, 0.5f),
    labelSmall = style(11, 16, W500, 0.5f),
    displayLargeEmphasized = style(57, 64, W600, -0.25f),
    displayMediumEmphasized = style(45, 52, W600),
    displaySmallEmphasized = style(36, 44, W600),
    headlineLargeEmphasized = style(32, 40, W600),
    headlineMediumEmphasized = style(28, 36, W600),
    headlineSmallEmphasized = style(24, 32, W600),
    titleLargeEmphasized = style(22, 28, W500),
    titleMediumEmphasized = style(16, 24, W600, 0.15f),
    titleSmallEmphasized = style(14, 20, W600, 0.1f),
    bodyLargeEmphasized = style(16, 24, W500, 0.5f),
    bodyMediumEmphasized = style(14, 20, W500, 0.25f),
    bodySmallEmphasized = style(12, 16, W500, 0.4f),
    labelLargeEmphasized = style(14, 20, W600, 0.1f),
    labelMediumEmphasized = style(12, 16, W600, 0.5f),
    labelSmallEmphasized = style(11, 16, W600, 0.5f),
)

// ---------------------------------------------------------------------------
// Migration shims — screens still reference these; remove once they are migrated.
// ---------------------------------------------------------------------------

@Deprecated("migration shim: use MaterialTheme.typography / GoogleSansFlex")
val InstrumentSerif: FontFamily = GoogleSansFlex

@Deprecated("migration shim: use MaterialTheme.typography / GoogleSansFlex")
val InterTight: FontFamily = GoogleSansFlex

@Deprecated("migration shim: use MaterialTheme.typography / GoogleSansFlex")
val JetBrainsMono: FontFamily = GoogleSansFlex

@Deprecated("migration shim: use MaterialTheme.typography.labelMedium")
val MonoLabelStyle = TextStyle(
    fontFamily = GoogleSansFlex,
    fontWeight = W500,
    fontSize = 12.sp,
    letterSpacing = 0.5.sp,
)

@Deprecated("migration shim: use MaterialTheme.typography.labelSmall")
val MonoSmallStyle = TextStyle(
    fontFamily = GoogleSansFlex,
    fontWeight = W500,
    fontSize = 11.sp,
    letterSpacing = 0.5.sp,
)

@Deprecated("migration shim: use MaterialTheme.typography.bodyMedium")
val MonoBodyStyle = TextStyle(
    fontFamily = GoogleSansFlex,
    fontWeight = W400,
    fontSize = 14.sp,
    letterSpacing = 0.25.sp,
)

@Deprecated("migration shim: use MaterialTheme.typography.labelSmall with tnum")
val MonoTimestampStyle = TextStyle(
    fontFamily = GoogleSansFlex,
    fontWeight = W400,
    fontSize = 11.sp,
    letterSpacing = 0.4.sp,
    fontFeatureSettings = "tnum",
)
