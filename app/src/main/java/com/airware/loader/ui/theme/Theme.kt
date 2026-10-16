package com.airware.loader.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// Акцент совпадает с палитрой самого чита (розовый #EC407A)
val AccentPink = Color(0xFFEC407A)
val AccentPinkDim = Color(0x30EC407A)

private val DarkColors = darkColorScheme(
    primary = AccentPink,
    onPrimary = Color(0xFF1A1A1E),
    secondary = Color(0xFF9E9EA8),
    background = Color(0xFF0B0B0E),
    onBackground = Color(0xFFF0F0F5),
    surface = Color(0xFF121216),
    onSurface = Color(0xFFF0F0F5),
    surfaceVariant = Color(0xFF1A1A20),
    onSurfaceVariant = Color(0xFF9E9EA8),
    outline = Color(0xFF28282E),
    error = Color(0xFFEF5350),
)

@Composable
fun AirwareLoaderTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = DarkColors,
        typography = MaterialTheme.typography,
        content = content
    )
}
