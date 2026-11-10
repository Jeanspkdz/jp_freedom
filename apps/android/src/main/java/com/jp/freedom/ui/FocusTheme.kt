package com.jp.freedom.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val NightColors = darkColorScheme(
    primary = Color(0xFFAAB9FF), onPrimary = Color(0xFF10182B),
    primaryContainer = Color(0xFF293653), onPrimaryContainer = Color(0xFFE1E7FF),
    secondary = Color(0xFFBCC5E0), onSecondary = Color(0xFF202A40),
    secondaryContainer = Color(0xFF27324A), onSecondaryContainer = Color(0xFFDCE3FC),
    background = Color(0xFF10182B), onBackground = Color(0xFFF2F0EA),
    surface = Color(0xFF18233A), onSurface = Color(0xFFF2F0EA),
    surfaceVariant = Color(0xFF222E47), onSurfaceVariant = Color(0xFFB4BED8),
    outline = Color(0xFF7785A5), outlineVariant = Color(0xFF34415D),
    error = Color(0xFFFFB4AB), onError = Color(0xFF690005),
    tertiary = Color(0xFFFFC27A), onTertiary = Color(0xFF402D12),
    tertiaryContainer = Color(0xFF563C1D), onTertiaryContainer = Color(0xFFFFDDB2),
)

@Composable
fun FocusTheme(
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = NightColors,
        content = content,
    )
}
