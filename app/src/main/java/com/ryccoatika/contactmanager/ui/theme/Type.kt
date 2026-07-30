package com.ryccoatika.contactmanager.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.ryccoatika.contactmanager.R

/**
 * Manrope, bundled as a single variable TTF. Each weight declares the matching
 * `wght` axis value so the renderer picks the right instance on API 26+; older
 * devices fall back to the font's default (Regular) instance.
 */
@OptIn(ExperimentalTextApi::class)
private fun manrope(weight: FontWeight, axis: Int) =
    Font(R.font.manrope, weight = weight, variationSettings = FontVariation.Settings(FontVariation.weight(axis)))

val AppFontFamily = FontFamily(
    manrope(FontWeight.Normal, 400),
    manrope(FontWeight.Medium, 500),
    manrope(FontWeight.SemiBold, 600),
    manrope(FontWeight.Bold, 700),
    manrope(FontWeight.ExtraBold, 800),
)

/** Line up digits in phone numbers, counts, and codes. */
val TabularNums = TextStyle(fontFeatureSettings = "tnum")

private val base = Typography()
val Typography = Typography(
    displayLarge = base.displayLarge.copy(fontFamily = AppFontFamily, fontWeight = FontWeight.ExtraBold, letterSpacing = (-0.5).sp),
    displayMedium = base.displayMedium.copy(fontFamily = AppFontFamily, fontWeight = FontWeight.ExtraBold, letterSpacing = (-0.5).sp),
    displaySmall = base.displaySmall.copy(fontFamily = AppFontFamily, fontWeight = FontWeight.ExtraBold, letterSpacing = (-0.5).sp),
    headlineLarge = base.headlineLarge.copy(fontFamily = AppFontFamily, fontWeight = FontWeight.Bold, letterSpacing = (-0.5).sp),
    headlineMedium = base.headlineMedium.copy(fontFamily = AppFontFamily, fontWeight = FontWeight.Bold, letterSpacing = (-0.4).sp),
    headlineSmall = base.headlineSmall.copy(fontFamily = AppFontFamily, fontWeight = FontWeight.SemiBold, letterSpacing = (-0.3).sp),
    titleLarge = base.titleLarge.copy(fontFamily = AppFontFamily, fontWeight = FontWeight.Bold, letterSpacing = (-0.3).sp),
    titleMedium = base.titleMedium.copy(fontFamily = AppFontFamily, fontWeight = FontWeight.SemiBold),
    titleSmall = base.titleSmall.copy(fontFamily = AppFontFamily, fontWeight = FontWeight.SemiBold),
    bodyLarge = base.bodyLarge.copy(fontFamily = AppFontFamily),
    bodyMedium = base.bodyMedium.copy(fontFamily = AppFontFamily),
    bodySmall = base.bodySmall.copy(fontFamily = AppFontFamily),
    labelLarge = base.labelLarge.copy(fontFamily = AppFontFamily, fontWeight = FontWeight.SemiBold),
    labelMedium = base.labelMedium.copy(fontFamily = AppFontFamily, fontWeight = FontWeight.SemiBold),
    labelSmall = base.labelSmall.copy(fontFamily = AppFontFamily, fontWeight = FontWeight.Medium, letterSpacing = 0.4.sp),
)
