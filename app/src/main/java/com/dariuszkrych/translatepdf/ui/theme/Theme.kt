package com.dariuszkrych.translatepdf.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val DarkColorScheme = darkColorScheme(
    primary = White,
    onPrimary = Black,
    primaryContainer = DarkGrey,
    onPrimaryContainer = White,
    secondary = White,
    onSecondary = Black,
    secondaryContainer = DarkGrey,
    onSecondaryContainer = White,
    tertiary = White,
    onTertiary = Black,
    tertiaryContainer = DarkGrey,
    onTertiaryContainer = White,
    background = Black,
    onBackground = White,
    surface = Black,
    onSurface = White,
    surfaceVariant = DarkGrey,
    onSurfaceVariant = White,
    surfaceTint = Color.Transparent,
    inverseSurface = White,
    inverseOnSurface = Black,
    inversePrimary = Black,
    outline = White,
    outlineVariant = DarkGrey,
    surfaceBright = DarkGrey,
    surfaceDim = Black,
    surfaceContainer = DarkGrey,
    surfaceContainerHigh = DarkGrey,
    surfaceContainerHighest = DarkGrey,
    surfaceContainerLow = Black,
    surfaceContainerLowest = Black
)

private val LightColorScheme = lightColorScheme(
    primary = Black,
    onPrimary = White,
    primaryContainer = LightGrey,
    onPrimaryContainer = Black,
    secondary = Black,
    onSecondary = White,
    secondaryContainer = LightGrey,
    onSecondaryContainer = Black,
    tertiary = Black,
    onTertiary = White,
    tertiaryContainer = LightGrey,
    onTertiaryContainer = Black,
    background = White,
    onBackground = Black,
    surface = White,
    onSurface = Black,
    surfaceVariant = LightGrey,
    onSurfaceVariant = Black,
    surfaceTint = Color.Transparent,
    inverseSurface = Black,
    inverseOnSurface = White,
    inversePrimary = White,
    outline = Black,
    outlineVariant = LightGrey,
    surfaceBright = White,
    surfaceDim = LightGrey,
    surfaceContainer = LightGrey,
    surfaceContainerHigh = LightGrey,
    surfaceContainerHighest = LightGrey,
    surfaceContainerLow = White,
    surfaceContainerLowest = White
)

@Composable
fun TranslatePDFTheme(
    content: @Composable () -> Unit
) {
    val colorScheme = if (ThemeState.isDark) DarkColorScheme else LightColorScheme

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
