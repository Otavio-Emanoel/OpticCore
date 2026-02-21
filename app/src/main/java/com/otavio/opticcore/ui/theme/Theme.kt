package com.otavio.opticcore.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

/**
 * OpticCore sempre usa dark theme — é um app de câmera profissional.
 */
private val OpticCoreDarkScheme = darkColorScheme(
    primary = AccentBlue,
    onPrimary = Color.White,
    primaryContainer = AccentBlueDark,
    onPrimaryContainer = AccentBlueLight,

    secondary = StatusCyan,
    onSecondary = Color.Black,

    tertiary = StatusAmber,
    onTertiary = Color.Black,

    background = DarkBackground,
    onBackground = TextPrimary,

    surface = DarkSurface,
    onSurface = TextPrimary,

    surfaceVariant = DarkCard,
    onSurfaceVariant = TextSecondary,

    outline = DividerColor,
    outlineVariant = DividerColor,

    error = StatusRed,
    onError = Color.White
)

@Composable
fun OpticCoreTheme(
    content: @Composable () -> Unit
) {
    val colorScheme = OpticCoreDarkScheme

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = DarkSurface.toArgb()
            window.navigationBarColor = DarkSurface.toArgb()
            WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars = false
                isAppearanceLightNavigationBars = false
            }
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}