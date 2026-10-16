package com.filemanager.app.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Design tokens taken from the U.S. Web Design System (USWDS) and GOV.UK:
 * high-contrast blue, near-black ink, square-ish corners, 16sp+ body text,
 * a yellow focus ring, and red reserved for errors and destructive actions.
 */
object Gov {
    val Blue = Color(0xFF005EA2)
    val BlueLight = Color(0xFF73B3E7)
    val BlueLighter = Color(0xFFD9E8F6)
    val BlueDarker = Color(0xFF162E51)
    val Ink = Color(0xFF1B1B1B)
    val GreyDark = Color(0xFF565C65)
    val GreyLight = Color(0xFFDFE1E2)
    val GreyLightest = Color(0xFFF0F0F0)
    val Red = Color(0xFFB50909)
    val RedLight = Color(0xFFF58B7B)
    val FocusYellow = Color(0xFFFFDD00)

    // Category colours. Always shown next to a text label, never the only cue.
    val Folder = Color(0xFF936F38)
    val Images = Color(0xFF2378C3)
    val Videos = Color(0xFFD83933)
    val Audio = Color(0xFF8168B3)
    val Documents = Color(0xFF2E8B85)
    val Apps = Color(0xFF5A9A63)
    val Other = Color(0xFF71767A)
}

private val LightColors = lightColorScheme(
    primary = Gov.Blue, onPrimary = Color.White,
    primaryContainer = Gov.BlueLighter, onPrimaryContainer = Gov.BlueDarker,
    secondary = Gov.GreyDark, onSecondary = Color.White,
    secondaryContainer = Gov.GreyLightest, onSecondaryContainer = Gov.Ink,
    background = Color.White, onBackground = Gov.Ink,
    surface = Color.White, onSurface = Gov.Ink,
    surfaceVariant = Gov.GreyLightest, onSurfaceVariant = Gov.GreyDark,
    surfaceTint = Gov.Blue,
    inverseSurface = Gov.Ink, inverseOnSurface = Color.White,
    error = Gov.Red, onError = Color.White,
    errorContainer = Color(0xFFF4E3DB), onErrorContainer = Color(0xFF6F0A0A),
    outline = Color(0xFF71767A), outlineVariant = Gov.GreyLight,
    surfaceContainerLowest = Color.White, surfaceContainerLow = Color.White,
    surfaceContainer = Color(0xFFF7F7F7), surfaceContainerHigh = Color.White,
    surfaceContainerHighest = Gov.GreyLightest
)

private val DarkColors = darkColorScheme(
    primary = Gov.BlueLight, onPrimary = Color(0xFF0B1F33),
    primaryContainer = Color(0xFF1A4480), onPrimaryContainer = Gov.BlueLighter,
    secondary = Color(0xFFA9AEB1), onSecondary = Gov.Ink,
    secondaryContainer = Color(0xFF2E2E2E), onSecondaryContainer = Gov.GreyLightest,
    background = Color(0xFF1B1B1B), onBackground = Gov.GreyLightest,
    surface = Color(0xFF1B1B1B), onSurface = Gov.GreyLightest,
    surfaceVariant = Color(0xFF2E2E2E), onSurfaceVariant = Color(0xFFC9C9C9),
    surfaceTint = Gov.BlueLight,
    inverseSurface = Gov.GreyLightest, inverseOnSurface = Gov.Ink,
    error = Gov.RedLight, onError = Color(0xFF3E0A00),
    errorContainer = Color(0xFF5C1A12), onErrorContainer = Color(0xFFFFDAD3),
    outline = Color(0xFFA9AEB1), outlineVariant = Color(0xFF454545),
    surfaceContainerLowest = Color(0xFF141414), surfaceContainerLow = Color(0xFF1B1B1B),
    surfaceContainer = Color(0xFF242424), surfaceContainerHigh = Color(0xFF2A2A2A),
    surfaceContainerHighest = Color(0xFF303030)
)

private val AppTypography = Typography(
    headlineMedium = TextStyle(fontSize = 28.sp, lineHeight = 34.sp, fontWeight = FontWeight.Bold),
    titleLarge = TextStyle(fontSize = 22.sp, lineHeight = 28.sp, fontWeight = FontWeight.Bold),
    titleMedium = TextStyle(fontSize = 18.sp, lineHeight = 24.sp, fontWeight = FontWeight.Bold),
    titleSmall = TextStyle(fontSize = 16.sp, lineHeight = 22.sp, fontWeight = FontWeight.Bold),
    bodyLarge = TextStyle(fontSize = 17.sp, lineHeight = 24.sp),
    bodyMedium = TextStyle(fontSize = 16.sp, lineHeight = 22.sp),
    bodySmall = TextStyle(fontSize = 14.sp, lineHeight = 20.sp),
    labelLarge = TextStyle(fontSize = 16.sp, lineHeight = 22.sp, fontWeight = FontWeight.Bold),
    labelMedium = TextStyle(fontSize = 14.sp, lineHeight = 18.sp, fontWeight = FontWeight.Medium),
    labelSmall = TextStyle(fontSize = 12.sp, lineHeight = 16.sp, fontWeight = FontWeight.Medium)
)

private val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(2.dp),
    small = RoundedCornerShape(4.dp),
    medium = RoundedCornerShape(4.dp),
    large = RoundedCornerShape(4.dp),
    extraLarge = RoundedCornerShape(8.dp)
)

@Composable
fun FileManagerTheme(darkTheme: Boolean, content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        typography = AppTypography,
        shapes = AppShapes,
        content = content
    )
}
