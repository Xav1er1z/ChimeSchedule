package com.example.courseschedule.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private fun lightColors(accent: Color) = lightColorScheme(
    primary = accent,
    onPrimary = Color.White,
    primaryContainer = accent.copy(alpha = 0.15f),
    onPrimaryContainer = accent,
    secondary = Color(0xFF5856D6),
    onSecondary = Color.White,
    background = LightBackground,
    onBackground = Color(0xFF000000),
    surface = LightCard,
    onSurface = Color(0xFF000000),
    surfaceVariant = LightBackground,
    onSurfaceVariant = LightSecondaryText,
    outline = LightSeparator,
    outlineVariant = LightSeparator,
    error = Color(0xFFFF3B30),
    onError = Color.White,
)

private fun darkColors(accent: Color) = darkColorScheme(
    primary = accent,
    onPrimary = Color.White,
    primaryContainer = accent.copy(alpha = 0.25f),
    onPrimaryContainer = Color.White,
    secondary = Color(0xFF5E5CE6),
    onSecondary = Color.White,
    background = DarkBackground,
    onBackground = Color.White,
    surface = DarkCard,
    onSurface = Color.White,
    surfaceVariant = Color(0xFF2C2C2E),
    onSurfaceVariant = DarkSecondaryText,
    outline = DarkSeparator,
    outlineVariant = DarkSeparator,
    error = Color(0xFFFF453A),
    onError = Color.White,
)

@Composable
fun CourseScheduleTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    accentColor: Color = Color(0xFF007AFF),
    content: @Composable () -> Unit,
) {
    val scheme = if (darkTheme) darkColors(accentColor) else lightColors(accentColor)
    MaterialTheme(
        colorScheme = scheme,
        typography = AppTypography,
        content = content,
    )
}
