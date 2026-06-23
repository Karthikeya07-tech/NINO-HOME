package com.example.nino_home.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val LightColorScheme = lightColorScheme(
    primary = NinoRed,
    onPrimary = NinoWhite,
    primaryContainer = NinoRed,
    onPrimaryContainer = NinoWhite,
    secondary = NinoBlack,
    onSecondary = NinoWhite,
    secondaryContainer = NinoBlack,
    onSecondaryContainer = NinoWhite,
    tertiary = NinoRed,
    onTertiary = NinoWhite,
    background = NinoWhite,
    onBackground = NinoBlack,
    surface = NinoWhite,
    onSurface = NinoBlack,
    surfaceVariant = NinoWhite,
    onSurfaceVariant = NinoBlack,
    error = NinoRed,
    onError = NinoWhite,
    outline = NinoBlack,
    outlineVariant = NinoBlack,
    scrim = NinoBlack,
    inverseSurface = NinoBlack,
    inverseOnSurface = NinoWhite,
    inversePrimary = NinoRed,
    surfaceContainer = NinoWhite,
    surfaceContainerHigh = NinoWhite,
    surfaceContainerHighest = NinoWhite,
    surfaceContainerLow = NinoWhite,
    surfaceContainerLowest = NinoWhite,
)

private val DarkColorScheme = darkColorScheme(
    primary = NinoRed,
    onPrimary = NinoWhite,
    primaryContainer = NinoRed,
    onPrimaryContainer = NinoWhite,
    secondary = NinoWhite,
    onSecondary = NinoBlack,
    secondaryContainer = NinoWhite,
    onSecondaryContainer = NinoBlack,
    tertiary = NinoRed,
    onTertiary = NinoWhite,
    background = NinoBlack,
    onBackground = NinoWhite,
    surface = NinoBlack,
    onSurface = NinoWhite,
    surfaceVariant = NinoBlack,
    onSurfaceVariant = NinoWhite,
    error = NinoRed,
    onError = NinoWhite,
    outline = NinoWhite,
    outlineVariant = NinoWhite,
    scrim = NinoBlack,
    inverseSurface = NinoWhite,
    inverseOnSurface = NinoBlack,
    inversePrimary = NinoRed,
    surfaceContainer = NinoBlack,
    surfaceContainerHigh = NinoBlack,
    surfaceContainerHighest = NinoBlack,
    surfaceContainerLow = NinoBlack,
    surfaceContainerLowest = NinoBlack,
)

@Composable
fun NinoHomeTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme
    val view = LocalView.current

    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = colorScheme.primary.toArgb()
            window.navigationBarColor = colorScheme.background.toArgb()
            WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars = false
                isAppearanceLightNavigationBars = !darkTheme
            }
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content,
    )
}
