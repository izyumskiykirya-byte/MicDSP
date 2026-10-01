package com.example.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// High contrast automotive dark scheme optimized for in-vehicle head units
private val CarDarkColorScheme = darkColorScheme(
    primary = CarCyan,
    onPrimary = Color.Black,
    primaryContainer = Color(0xFF00363A),
    onPrimaryContainer = CarCyan,
    secondary = CarAmber,
    onSecondary = Color.Black,
    secondaryContainer = Color(0xFF452B00),
    onSecondaryContainer = CarAmberBright,
    tertiary = CarGreen,
    background = CarBackground,
    onBackground = CarTextPrimary,
    surface = CarSurface,
    onSurface = CarTextPrimary,
    surfaceVariant = CarSurfaceVariant,
    onSurfaceVariant = CarTextSecondary,
    outline = CarCardBorder,
    error = CarRed,
    onError = Color.White
)

@Composable
fun MyApplicationTheme(
    darkTheme: Boolean = true, // In-car head unit is always high-contrast dark mode
    dynamicColor: Boolean = false, // Keep consistent automotive cockpit theme
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = CarDarkColorScheme,
        typography = Typography,
        content = content
    )
}
