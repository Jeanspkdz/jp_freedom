package com.jp.freedom.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val LightColors = lightColorScheme(
    primary = Color(0xFFA65608),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFF9E0B4),
    onPrimaryContainer = Color(0xFF603300),
    secondary = Color(0xFF50545B),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFE9E9ED),
    onSecondaryContainer = Color(0xFF22232A),
    background = Color(0xFFF7F6F6),
    onBackground = Color(0xFF0A0A0E),
    surface = Color(0xFFFFFEFE),
    onSurface = Color(0xFF0A0A0E),
    surfaceVariant = Color(0xFFEBEBEF),
    onSurfaceVariant = Color(0xFF36363D),
    outline = Color(0xFFD4D4DA),
    outlineVariant = Color(0xFFE3E3E7),
    error = Color(0xFFBA1A1A),
    onError = Color(0xFFFFFFFF),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFFFFB95E),
    onPrimary = Color(0xFF522C00),
    primaryContainer = Color(0xFF6B3A00),
    onPrimaryContainer = Color(0xFFFFE0B4),
    secondary = Color(0xFFBDC2C8),
    onSecondary = Color(0xFF2D3136),
    secondaryContainer = Color(0xFF414148),
    onSecondaryContainer = Color(0xFFE2E2E8),
    background = Color(0xFF141414),
    onBackground = Color(0xFFF0EFF1),
    surface = Color(0xFF161616),
    onSurface = Color(0xFFF0EFF1),
    surfaceVariant = Color(0xFF2B2B30),
    onSurfaceVariant = Color(0xFFC5C5CC),
    outline = Color(0xFF8A8A91),
    outlineVariant = Color(0xFF414148),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
)

@Composable
fun FocusTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        content = content,
    )
}
