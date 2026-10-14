package com.puttvision.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

private val DarkColorScheme = darkColorScheme(
    primary          = PuttGreen,
    onPrimary        = Background,
    primaryContainer = PuttGreenDim,
    secondary        = ImpactAmber,
    onSecondary      = Background,
    error            = ErrorRed,
    background       = Background,
    onBackground     = TextPrimary,
    surface          = SurfaceMid,
    onSurface        = TextPrimary,
    surfaceVariant   = SurfaceHigh,
    onSurfaceVariant = TextSecondary,
    outline          = SurfaceHigh
)

/**
 * PuttVision always forces dark theme — the app is designed for high-contrast
 * outdoor/indoor training environments where white backgrounds are blinding.
 */
@Composable
fun PuttVisionTheme(
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = DarkColorScheme,
        typography  = PuttVisionTypography,
        content     = content
    )
}
