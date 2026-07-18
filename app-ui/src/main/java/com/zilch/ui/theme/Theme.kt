package com.zilch.ui.theme

import android.app.Activity
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val DarkColorScheme = darkColorScheme(
    primary = DarkPalette.primary,
    onPrimary = DarkPalette.onPrimary,
    primaryContainer = DarkPalette.primaryVariant,
    secondary = DarkPalette.secondary,
    onSecondary = DarkPalette.onSecondary,
    secondaryContainer = DarkPalette.secondaryVariant,
    tertiary = DarkPalette.tertiary,
    onTertiary = DarkPalette.onTertiary,
    tertiaryContainer = DarkPalette.tertiaryVariant,
    background = DarkPalette.background,
    onBackground = DarkPalette.onBackground,
    surface = DarkPalette.surface,
    onSurface = DarkPalette.onSurface,
    surfaceVariant = DarkPalette.surfaceVariant,
    onSurfaceVariant = DarkPalette.onSurfaceVariant,
    error = DarkPalette.error,
    onError = DarkPalette.onError,
    errorContainer = DarkPalette.errorContainer,
    outline = DarkPalette.border,
    outlineVariant = DarkPalette.divider,
)

/**
 * Tema principal de Zilch — Dark Only.
 *
 * No hay light theme. La app siempre opera en modo oscuro
 * para maximizar privacidad y minimizar consumo de batería.
 */
@Composable
fun ZilchTheme(content: @Composable () -> Unit) {
    val view = LocalView.current

    if (!view.isInEditMode) {
        @Suppress("DEPRECATION")
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = DarkPalette.background.toArgb()
            window.navigationBarColor = DarkPalette.background.toArgb()
            WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars = false
                isAppearanceLightNavigationBars = false
            }
        }
    }

    MaterialTheme(
        colorScheme = DarkColorScheme,
        typography = ZilchTypography,
        content = content
    )
}
