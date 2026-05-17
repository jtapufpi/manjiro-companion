package com.jtapzg.manjirogaming.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val ManjiroDarkScheme = darkColorScheme(
    primary = MgColors.Red,
    onPrimary = MgColors.TextPrimary,
    primaryContainer = MgColors.RedDim,
    onPrimaryContainer = MgColors.TextPrimary,
    secondary = MgColors.Amber,
    onSecondary = MgColors.Bg,
    secondaryContainer = MgColors.CardHigh,
    onSecondaryContainer = MgColors.TextPrimary,
    background = MgColors.Bg,
    onBackground = MgColors.TextPrimary,
    surface = MgColors.Card,
    onSurface = MgColors.TextPrimary,
    surfaceVariant = MgColors.CardHigh,
    onSurfaceVariant = MgColors.TextSecondary,
    outline = MgColors.Border,
    outlineVariant = MgColors.Border,
    error = MgColors.Hot,
    onError = MgColors.TextPrimary
)

@Composable
fun ManjiroGamingTheme(
    @Suppress("UNUSED_PARAMETER")
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    // Sempre dark — visual Game Space.
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = MgColors.Bg.toArgb()
            window.navigationBarColor = MgColors.Bg.toArgb()
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = false
            WindowCompat.getInsetsController(window, view).isAppearanceLightNavigationBars = false
        }
    }
    MaterialTheme(
        colorScheme = ManjiroDarkScheme,
        typography = MgTypography,
        content = content
    )
}
