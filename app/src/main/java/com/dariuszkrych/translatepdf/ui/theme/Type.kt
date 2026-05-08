package com.dariuszkrych.translatepdf.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/**
 * Typography table handed to MaterialTheme. Only bodyLarge is customised. Every
 * other role falls back to the Material 3 defaults, which is what we want for a
 * minimal theme. Adding more overrides below would tune titles and labels too.
 */
val Typography = Typography(
    // Default body text. 16sp regular with 24sp line height for comfortable reading.
    bodyLarge = TextStyle(
        fontFamily = FontFamily.Default,   // System default font (Roboto on most devices).
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        lineHeight = 24.sp,
        letterSpacing = 0.5.sp
    )
    /* Other default text styles to override
    titleLarge = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Normal,
        fontSize = 22.sp,
        lineHeight = 28.sp,
        letterSpacing = 0.sp
    ),
    labelSmall = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Medium,
        fontSize = 11.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.5.sp
    )
    */
)
