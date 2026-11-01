package com.fastmask.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontVariation
import com.fastmask.R
import androidx.compose.ui.unit.sp

/**
 * Google Sans Flex, bundled (SIL OFL 1.1, see licenses/GoogleSansFlex-OFL.txt).
 *
 * It is NOT in the Google Play services downloadable-fonts catalog, so the
 * provider silently fell back to Roboto. One variable file serves every weight;
 * [GoogleSansFlexRounded] turns the ROND axis up for the emphasized styles —
 * the soft, rounded headline voice of Material 3 Expressive.
 */
@OptIn(ExperimentalTextApi::class)
private fun gsf(weight: FontWeight, round: Float) = Font(
    resId = R.font.google_sans_flex,
    weight = weight,
    variationSettings = FontVariation.Settings(
        FontVariation.weight(weight.weight),
        FontVariation.Setting("ROND", round),
    ),
)

private val Weights = listOf(FontWeight.Normal, FontWeight.Medium, FontWeight.SemiBold, FontWeight.Bold)

val GoogleSansFlex = FontFamily(Weights.map { gsf(it, 0f) })

val GoogleSansFlexRounded = FontFamily(Weights.map { gsf(it, 100f) })

private fun style(
    size: Int,
    line: Int,
    weight: FontWeight,
    tracking: Float = 0f,
    family: FontFamily = GoogleSansFlex,
) = TextStyle(
    fontFamily = family,
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
    displayLargeEmphasized = style(57, 64, W600, -0.25f, family = GoogleSansFlexRounded),
    displayMediumEmphasized = style(45, 52, W600, family = GoogleSansFlexRounded),
    displaySmallEmphasized = style(36, 44, W600, family = GoogleSansFlexRounded),
    headlineLargeEmphasized = style(32, 40, W600, family = GoogleSansFlexRounded),
    headlineMediumEmphasized = style(28, 36, W600, family = GoogleSansFlexRounded),
    headlineSmallEmphasized = style(24, 32, W600, family = GoogleSansFlexRounded),
    titleLargeEmphasized = style(22, 28, W500, family = GoogleSansFlexRounded),
    titleMediumEmphasized = style(16, 24, W600, 0.15f),
    titleSmallEmphasized = style(14, 20, W600, 0.1f),
    bodyLargeEmphasized = style(16, 24, W500, 0.5f),
    bodyMediumEmphasized = style(14, 20, W500, 0.25f),
    bodySmallEmphasized = style(12, 16, W500, 0.4f),
    labelLargeEmphasized = style(14, 20, W600, 0.1f),
    labelMediumEmphasized = style(12, 16, W600, 0.5f),
    labelSmallEmphasized = style(11, 16, W600, 0.5f),
)
