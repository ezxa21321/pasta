package com.airware.loader.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// Акцент — розовый/винный, как на референсе
val AccentPinkDark = Color(0xFFF2A6C6)   // акцент в тёмной теме (светлее, на тёмном фоне)
val AccentPinkLight = Color(0xFF7A2E45)  // акцент в светлой теме (винный, на светлом фоне)

private val DarkColors = darkColorScheme(
    primary = AccentPinkDark,
    onPrimary = Color(0xFF2B1620),
    secondary = Color(0xFFB7A4AC),
    background = Color(0xFF2B2024),
    onBackground = Color(0xFFF2E8EA),
    surface = Color(0xFF3A2B30),
    onSurface = Color(0xFFF2E8EA),
    surfaceVariant = Color(0xFF453439),
    onSurfaceVariant = Color(0xFFB7A4AC),
    outline = Color(0xFF4F3C42),
    error = Color(0xFFEF9A9A),
)

private val LightColors = lightColorScheme(
    primary = AccentPinkLight,
    onPrimary = Color(0xFFFDF2F5),
    secondary = Color(0xFF8A7076),
    background = Color(0xFFF7E3E9),
    onBackground = Color(0xFF3A2228),
    surface = Color(0xFFFDF2F5),
    onSurface = Color(0xFF3A2228),
    surfaceVariant = Color(0xFFF0DCE2),
    onSurfaceVariant = Color(0xFF8A7076),
    outline = Color(0xFFE3CCD4),
    error = Color(0xFFB3261E),
)

@Composable
fun AirwareLoaderTheme(darkTheme: Boolean, content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        typography = MaterialTheme.typography,
        content = content
    )
}
