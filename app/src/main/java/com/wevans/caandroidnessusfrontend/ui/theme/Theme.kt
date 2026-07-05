package com.wevans.caandroidnessusfrontend.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val DarkColorScheme = darkColorScheme(
    primary = CyberCyan,
    secondary = CyberCyanVariant,
    tertiary = CyberSlate,
    background = CyberNavy,
    surface = CyberBlue,
    surfaceVariant = Color(0xFF334155),
    onPrimary = CyberNavy,
    onSecondary = CyberWhite,
    onTertiary = CyberWhite,
    onBackground = CyberLight,
    onSurface = CyberLight,
    onSurfaceVariant = CyberLight,
    error = ErrorRed
)

private val LightColorScheme = lightColorScheme(
    primary = CyberCyanVariant,
    secondary = CyberCyan,
    tertiary = CyberSlate,
    background = CyberLight,
    surface = CyberWhite,
    surfaceVariant = Color(0xFFE2E8F0),
    onPrimary = CyberWhite,
    onSecondary = CyberNavy,
    onTertiary = CyberNavy,
    onBackground = CyberNavy,
    onSurface = CyberNavy,
    onSurfaceVariant = CyberNavy,
    error = ErrorRed
)

@Composable
fun NessusFrontendTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme 
    
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            // Proper edge-to-edge for targetSdk 35 / Android 15+
            WindowCompat.setDecorFitsSystemWindows(window, false)
            window.statusBarColor = Color.Transparent.toArgb()
            val controller = WindowCompat.getInsetsController(window, view)
            controller.isAppearanceLightStatusBars = !darkTheme
            controller.isAppearanceLightNavigationBars = !darkTheme
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
