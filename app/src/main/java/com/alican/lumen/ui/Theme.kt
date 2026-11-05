package com.alican.lumen.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val LumenColors = darkColorScheme(
    primary = Color(0xFFF5B44A),
    onPrimary = Color(0xFF1A1200),
    secondary = Color(0xFF8FB3C9),
    background = Color(0xFF08080A),
    onBackground = Color(0xFFEDEDF2),
    surface = Color(0xFF121216),
    onSurface = Color(0xFFEDEDF2),
    surfaceVariant = Color(0xFF1C1C22),
    onSurfaceVariant = Color(0xFFA9A9B6),
    outline = Color(0xFF33333D),
)

@Composable
fun LumenTheme(content: @Composable () -> Unit) {
    @Suppress("UNUSED_EXPRESSION")
    isSystemInDarkTheme()
    MaterialTheme(colorScheme = LumenColors, content = content)
}
