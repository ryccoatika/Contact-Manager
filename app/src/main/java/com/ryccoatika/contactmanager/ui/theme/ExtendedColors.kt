package com.ryccoatika.contactmanager.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import kotlin.math.absoluteValue

/** Tokens Material3's [androidx.compose.material3.ColorScheme] has no slot for. */
data class ExtendedColors(
    val brass: Color,
    val onBrass: Color,
    val brassContainer: Color,
    val onBrassContainer: Color,
)

val LightExtendedColors = ExtendedColors(BrassLight, OnBrassLight, BrassContainerLight, OnBrassContainerLight)
val DarkExtendedColors = ExtendedColors(BrassDark, OnBrassDark, BrassContainerDark, OnBrassContainerDark)

val LocalExtendedColors = staticCompositionLocalOf { LightExtendedColors }

val MaterialTheme.extendedColors: ExtendedColors
    @Composable @ReadOnlyComposable
    get() = LocalExtendedColors.current

/**
 * Desaturated jewel gradients for identity avatars. The gradient is chosen
 * deterministically from a key (usually the contact's name) so the same person
 * always gets the same color.
 */
object AvatarPalette {
    private val gradients = listOf(
        listOf(Color(0xFF2C7A70), Color(0xFF54B9AA)), // teal
        listOf(Color(0xFF48579A), Color(0xFF8090D4)), // indigo
        listOf(Color(0xFF7C4A72), Color(0xFFB681AC)), // plum
        listOf(Color(0xFF977132), Color(0xFFD4AC64)), // brass
        listOf(Color(0xFFA2545C), Color(0xFFD68D93)), // rose
        listOf(Color(0xFF3E5A63), Color(0xFF7196A2)), // slate
    )

    fun colorsFor(key: String): List<Color> =
        gradients[key.hashCode().absoluteValue % gradients.size]

    fun brushFor(key: String): Brush = Brush.linearGradient(colorsFor(key))
}
