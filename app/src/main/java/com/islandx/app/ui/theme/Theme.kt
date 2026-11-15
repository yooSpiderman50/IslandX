package com.islandx.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val Scheme = darkColorScheme(
    primary = Color(0xFF8FB0FF),
    onPrimary = Color(0xFF00184A),
    background = Color(0xFF101014),
    surface = Color(0xFF1B1B20),
    onSurface = Color(0xFFE6E6EA)
)

@Composable
fun IslandXTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = Scheme, content = content)
}
