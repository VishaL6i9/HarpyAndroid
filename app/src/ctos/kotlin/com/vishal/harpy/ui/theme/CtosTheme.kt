package com.vishal.harpy.ui.theme

import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

// ctOS Color Palette
val CtosBlack = Color(0xFF050505)
val CtosDarkGrey = Color(0xFF101010)
val CtosCyan = Color(0xFF00E5FF)
val CtosWhite = Color(0xFFF0F0F0)
val CtosRed = Color(0xFFFF3D00)

private val CtosDarkColorScheme = darkColorScheme(
    primary = CtosCyan,
    background = CtosBlack,
    surface = CtosDarkGrey,
    onPrimary = CtosBlack,
    onBackground = CtosWhite,
    onSurface = CtosWhite,
    error = CtosRed
)

private val CtosTypography = Typography(
    titleLarge = TextStyle(
        fontFamily = FontFamily.Monospace,
        fontWeight = FontWeight.Black,
        fontSize = 22.sp,
        letterSpacing = 2.sp
    ),
    titleMedium = TextStyle(
        fontFamily = FontFamily.Monospace,
        fontWeight = FontWeight.Bold,
        fontSize = 18.sp,
        letterSpacing = 1.sp
    ),
    bodyLarge = TextStyle(
        fontFamily = FontFamily.Monospace,
        fontSize = 16.sp
    ),
    bodyMedium = TextStyle(
        fontFamily = FontFamily.Monospace,
        fontSize = 14.sp
    ),
    labelSmall = TextStyle(
        fontFamily = FontFamily.Monospace,
        fontWeight = FontWeight.Bold,
        fontSize = 10.sp,
        letterSpacing = 1.sp
    )
)

@Composable
fun CtosTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = CtosDarkColorScheme,
        typography = CtosTypography,
        content = content
    )
}
