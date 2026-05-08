package com.dariuszkrych.translatepdf.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// Material 3 color scheme used when the app is running in dark mode.
// Every onX color is the foreground drawn on top of its matching container color.
private val DarkColorScheme = darkColorScheme(
    primary = White,                         // Main accent. Buttons and toolbar icons.
    onPrimary = Black,                       // Text and icons drawn on primary surfaces.
    primaryContainer = DarkGrey,             // Filled containers derived from primary.
    onPrimaryContainer = White,              // Content on primaryContainer.
    secondary = White,                       // Secondary accent. Used sparingly, kept consistent.
    onSecondary = Black,
    secondaryContainer = DarkGrey,
    onSecondaryContainer = White,
    tertiary = White,                        // Tertiary accent. Mirrors primary for this two tone theme.
    onTertiary = Black,
    tertiaryContainer = DarkGrey,
    onTertiaryContainer = White,
    background = Black,                      // Root background behind every screen.
    onBackground = White,                    // Default text color on background.
    surface = Black,                         // Cards and sheets background.
    onSurface = White,                       // Text and icons drawn on surfaces.
    surfaceVariant = DarkGrey,               // Alternate surface used for chips and dividers.
    onSurfaceVariant = White,
    surfaceTint = Color.Transparent,         // Disable the default M3 elevation tint overlay.
    inverseSurface = White,                  // Inverted surface (snackbars and similar).
    inverseOnSurface = Black,
    inversePrimary = Black,
    outline = White,                         // Border color for outlined buttons and text fields.
    outlineVariant = DarkGrey,               // Subtler outline for dividers.
    surfaceBright = DarkGrey,                // Elevated surface tones, all mapped to DarkGrey or Black
    surfaceDim = Black,                      // so the theme stays strictly two tone.
    surfaceContainer = DarkGrey,
    surfaceContainerHigh = DarkGrey,
    surfaceContainerHighest = DarkGrey,
    surfaceContainerLow = Black,
    surfaceContainerLowest = Black
)

// Material 3 color scheme used when the app is running in light mode.
// Mirrors the dark scheme with black and white swapped for a high contrast look.
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

/**
 * Top level Compose theme wrapper. Every Compose UI in the app is hosted inside
 * this so it picks up the correct light or dark palette and the shared typography.
 *
 * Reads [ThemeState.isDark], a Compose observable flag updated by the activity
 * and the settings screen, to decide which scheme to apply.
 */
@Composable
fun TranslatePDFTheme(
    content: @Composable () -> Unit
) {
    // Pick the palette once per recomposition based on the live theme flag.
    val colorScheme = if (ThemeState.isDark) DarkColorScheme else LightColorScheme

    // Apply scheme and typography to the subtree so any descendant composable can read them.
    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
