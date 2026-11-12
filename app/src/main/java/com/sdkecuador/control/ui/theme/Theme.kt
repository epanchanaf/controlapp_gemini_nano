package com.sdkecuador.control.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

private val TravelColorScheme = darkColorScheme(
    primary = TravelColors.cyan,
    onPrimary = TravelColors.background,
    secondary = TravelColors.muted,
    onSecondary = TravelColors.background,
    tertiary = TravelColors.amber,
    background = TravelColors.background,
    onBackground = TravelColors.text,
    surface = TravelColors.surface,
    onSurface = TravelColors.text,
    surfaceVariant = TravelColors.surfaceRaised,
    onSurfaceVariant = TravelColors.muted,
    outline = TravelColors.border,
    error = TravelColors.amber,
    onError = TravelColors.background,
)

@Composable
fun ControlTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = TravelColorScheme, typography = Typography, content = content)
}
