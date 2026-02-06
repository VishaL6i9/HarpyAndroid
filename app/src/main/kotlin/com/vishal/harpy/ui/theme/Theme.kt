package com.vishal.harpy.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val DarkColorScheme = darkColorScheme(
    primary = HarpyPrimary,
    onPrimary = HarpyOnPrimary,
    primaryContainer = Color(0xFF003D5C),
    onPrimaryContainer = Color(0xFFB3E5FC),
    
    secondary = HarpySecondary,
    onSecondary = HarpyOnSecondary,
    secondaryContainer = Color(0xFF004D5C),
    onSecondaryContainer = Color(0xFFB2EBF2),
    
    tertiary = HarpyTertiary,
    onTertiary = Color(0xFF000000),
    tertiaryContainer = Color(0xFF005662),
    onTertiaryContainer = Color(0xFFB2DFDB),
    
    error = HarpyError,
    onError = Color(0xFF000000),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6),
    
    background = HarpyBackground,
    onBackground = HarpyOnBackground,
    
    surface = HarpySurface,
    onSurface = HarpyOnSurface,
    surfaceVariant = HarpySurfaceVariant,
    onSurfaceVariant = HarpyOnSurfaceVariant,
    
    surfaceTint = HarpyPrimary,
    inverseSurface = Color(0xFFE1E1E1),
    inverseOnSurface = Color(0xFF000000),
    
    outline = HarpyOutline,
    outlineVariant = HarpyOutlineVariant,
    
    scrim = Color(0xFF000000),
    
    surfaceBright = Color(0xFF2A2A2A),
    surfaceDim = Color(0xFF000000),
    surfaceContainer = Color(0xFF0D0D0D),
    surfaceContainerHigh = Color(0xFF1A1A1A),
    surfaceContainerHighest = Color(0xFF262626),
    surfaceContainerLow = Color(0xFF050505),
    surfaceContainerLowest = Color(0xFF000000)
)

private val LightColorScheme = lightColorScheme(
    primary = Color(0xFF006494),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFCAE6FF),
    onPrimaryContainer = Color(0xFF001E31),
    
    secondary = Color(0xFF00696E),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFF6FF7FF),
    onSecondaryContainer = Color(0xFF002022),
    
    tertiary = Color(0xFF006874),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFF97F0FF),
    onTertiaryContainer = Color(0xFF001F24),
    
    error = Color(0xFFBA1A1A),
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF410002),
    
    background = Color(0xFFFCFCFF),
    onBackground = Color(0xFF1A1C1E),
    
    surface = Color(0xFFFCFCFF),
    onSurface = Color(0xFF1A1C1E),
    surfaceVariant = Color(0xFFDDE3EA),
    onSurfaceVariant = Color(0xFF41484D),
    
    outline = Color(0xFF71787E),
    outlineVariant = Color(0xFFC1C7CE),
    
    scrim = Color(0xFF000000)
)

@Composable
fun HarpyTheme(
    darkTheme: Boolean = true, // Always use dark theme for OLED
    content: @Composable () -> Unit
) {
    val colorScheme = DarkColorScheme // Always use dark color scheme

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
