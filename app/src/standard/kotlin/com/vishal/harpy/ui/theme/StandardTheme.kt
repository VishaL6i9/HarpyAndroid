package com.vishal.harpy.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

// Standard Color Palette
val StandardPrimary = Color(0xFF6200EE)
val StandardSecondary = Color(0xFF03DAC6)
val StandardTertiary = Color(0xFF03DAC6)
val StandardBackground = Color(0xFFFAFAFA)
val StandardSurface = Color(0xFFFFFFFF)
val StandardError = Color(0xFFB00020)

private val StandardDarkColorScheme = darkColorScheme(
    primary = StandardPrimary,
    secondary = StandardSecondary,
    tertiary = StandardTertiary,
    background = Color(0xFF121212),
    surface = Color(0xFF1E1E1E),
    error = StandardError
)

private val StandardLightColorScheme = lightColorScheme(
    primary = StandardPrimary,
    secondary = StandardSecondary,
    tertiary = StandardTertiary,
    background = StandardBackground,
    surface = StandardSurface,
    error = StandardError
)

private val StandardTypography = Typography(
    titleLarge = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Bold,
        fontSize = 22.sp
    ),
    titleMedium = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Bold,
        fontSize = 18.sp
    ),
    bodyLarge = TextStyle(
        fontFamily = FontFamily.Default,
        fontSize = 16.sp
    ),
    bodyMedium = TextStyle(
        fontFamily = FontFamily.Default,
        fontSize = 14.sp
    ),
    labelSmall = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Bold,
        fontSize = 10.sp
    )
)

@Composable
fun StandardTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) StandardDarkColorScheme else StandardLightColorScheme
    
    MaterialTheme(
        colorScheme = colorScheme,
        typography = StandardTypography,
        content = content
    )
}
