package com.example.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection

private val DarkColorScheme = darkColorScheme(
    primary = PowerOrange,
    onPrimary = Color.White,
    primaryContainer = PowerOrangeDark,
    onPrimaryContainer = Color.White,
    secondary = PowerOrangeLight,
    onSecondary = Color.Black,
    background = AthleticBlack,
    onBackground = Color(0xFFEEEEEE),
    surface = AthleticSurfaceDark,
    onSurface = Color(0xFFFFFFFF),
    surfaceVariant = AthleticCardDark,
    onSurfaceVariant = Color(0xFFE0E0E0),
    outline = AthleticBorderDark,
    error = StatusDanger,
    onError = Color.White
)

private val LightColorScheme = lightColorScheme(
    primary = PowerOrange,
    onPrimary = Color.White,
    primaryContainer = PowerOrangeLight,
    onPrimaryContainer = Color.Black,
    secondary = PowerOrangeDark,
    onSecondary = Color.White,
    background = AthleticSurfaceLight,
    onBackground = Color(0xFF1E1E1E),
    surface = AthleticWhite,
    onSurface = Color(0xFF1A1A1A),
    surfaceVariant = Color(0xFFF1F3F5),
    onSurfaceVariant = Color(0xFF333333),
    outline = AthleticBorderLight,
    error = StatusDanger,
    onError = Color.White
)

@Composable
fun PowerFeulTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme

    // Arabic RTL Layout direction enforcement
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = Typography,
            content = content
        )
    }
}
