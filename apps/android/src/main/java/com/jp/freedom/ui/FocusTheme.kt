package com.jp.freedom.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val LightColors = lightColorScheme(
    primary = Color(0xFF9B5A20),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFFFE8C5),
    onPrimaryContainer = Color(0xFF603300),
    secondary = Color(0xFF50545B),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFE9E9ED),
    onSecondaryContainer = Color(0xFF22232A),
    background = Color(0xFFFAF6EE),
    onBackground = Color(0xFF30251E),
    surface = Color(0xFFFFFDF8),
    onSurface = Color(0xFF30251E),
    surfaceVariant = Color(0xFFF2E9DC),
    onSurfaceVariant = Color(0xFF6F6257),
    outline = Color(0xFFC9B9A6),
    outlineVariant = Color(0xFFE5D9C9),
    error = Color(0xFFBA1A1A),
    onError = Color(0xFFFFFFFF),
)

@Composable
fun FocusTheme(
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = LightColors,
        content = content,
    )
}
