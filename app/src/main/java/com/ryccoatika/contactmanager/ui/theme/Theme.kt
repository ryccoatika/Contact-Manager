package com.ryccoatika.contactmanager.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

private val DarkColorScheme = darkColorScheme(
    primary = TealPrimaryDark,
    onPrimary = OnTealPrimaryDark,
    primaryContainer = TealContainerDark,
    onPrimaryContainer = OnTealContainerDark,
    secondary = SageSecondaryDark,
    onSecondary = OnSageSecondaryDark,
    secondaryContainer = SageContainerDark,
    onSecondaryContainer = OnSageContainerDark,
    tertiary = TerracottaTertiaryDark,
    onTertiary = OnTerracottaTertiaryDark,
    tertiaryContainer = TerracottaContainerDark,
    onTertiaryContainer = OnTerracottaContainerDark,
    background = WarmBackgroundDark,
    onBackground = OnWarmBackgroundDark,
    surface = WarmSurfaceDark,
    onSurface = OnWarmSurfaceDark,
    surfaceVariant = WarmSurfaceVariantDark,
    onSurfaceVariant = OnWarmSurfaceVariantDark,
    outline = WarmOutlineDark,
)

private val LightColorScheme = lightColorScheme(
    primary = TealPrimaryLight,
    onPrimary = OnTealPrimaryLight,
    primaryContainer = TealContainerLight,
    onPrimaryContainer = OnTealContainerLight,
    secondary = SageSecondaryLight,
    onSecondary = OnSageSecondaryLight,
    secondaryContainer = SageContainerLight,
    onSecondaryContainer = OnSageContainerLight,
    tertiary = TerracottaTertiaryLight,
    onTertiary = OnTerracottaTertiaryLight,
    tertiaryContainer = TerracottaContainerLight,
    onTertiaryContainer = OnTerracottaContainerLight,
    background = WarmBackgroundLight,
    onBackground = OnWarmBackgroundLight,
    surface = WarmSurfaceLight,
    onSurface = OnWarmSurfaceLight,
    surfaceVariant = WarmSurfaceVariantLight,
    onSurfaceVariant = OnWarmSurfaceVariantLight,
    outline = WarmOutlineLight,
)

/**
 * Dynamic color (Monet) on Android 12+, warm eye-comfort fallback palette
 * below; dark mode follows the system setting.
 */
@Composable
fun ContactManagerTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit,
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }

        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content,
    )
}
