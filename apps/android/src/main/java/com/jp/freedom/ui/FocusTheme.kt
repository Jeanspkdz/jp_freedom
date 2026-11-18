package com.jp.freedom.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

internal val TallyColors = darkColorScheme(
    primary = Color(0xFFFFC27A), onPrimary = Color(0xFF402D12),
    primaryContainer = Color(0xFF563C1D), onPrimaryContainer = Color(0xFFFFDDB2),
    secondary = Color(0xFFB39DDB), onSecondary = Color(0xFF2E243D),
    secondaryContainer = Color(0xFF47395F), onSecondaryContainer = Color(0xFFEBDDFF),
    background = Color(0xFF10182B), onBackground = Color(0xFFF2F0EA),
    surface = Color(0xFF18233A), onSurface = Color(0xFFF2F0EA),
    surfaceVariant = Color(0xFF222E47), onSurfaceVariant = Color(0xFFB4BED8),
    outline = Color(0xFF7785A5), outlineVariant = Color(0xFF34415D),
    error = Color(0xFFFFB4AB), onError = Color(0xFF690005),
    tertiary = Color(0xFFB39DDB), onTertiary = Color(0xFF2E243D),
    tertiaryContainer = Color(0xFF47395F), onTertiaryContainer = Color(0xFFEBDDFF),
)

@Composable
fun FocusTheme(
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = TallyColors,
        content = content,
    )
}
