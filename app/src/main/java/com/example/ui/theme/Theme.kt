package com.example.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val JarvisColorScheme = darkColorScheme(
    primary = JarvisPrimary,
    onPrimary = JarvisOnSecondaryContainer,
    primaryContainer = JarvisSurfaceVariant,
    onPrimaryContainer = JarvisPrimary,
    secondary = JarvisSecondary,
    onSecondary = JarvisOnSecondaryContainer,
    tertiary = JarvisTertiary,
    onTertiary = Color.White,
    background = JarvisBackground,
    onBackground = JarvisTextPrimary,
    surface = JarvisSurface,
    onSurface = JarvisTextPrimary,
    surfaceVariant = JarvisSurfaceVariant,
    onSurfaceVariant = JarvisTextSecondary,
    error = JarvisError,
    onError = Color.White
)

@Composable
fun MyApplicationTheme(
    darkTheme: Boolean = true, // Force dark theme for sci-fi JARVIS style
    dynamicColor: Boolean = false, // Disable dynamic colors to preserve our strict custom sci-fi theme
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = JarvisColorScheme,
        typography = Typography,
        content = content
    )
}
