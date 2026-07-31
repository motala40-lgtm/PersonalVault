package com.example.personalvault.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

val Purple80 = Color(0xFFD0BCFF)
val PurpleGrey80 = Color(0xFFCCC2DC)
val Pink80 = Color(0xFFEFB8C8)

val Purple40 = Color(0xFF6750A4)
val PurpleGrey40 = Color(0xFF625b71)
val Pink40 = Color(0xFF7D5260)

private val DarkColors = darkColorScheme(
    primary = Purple80,
    secondary = PurpleGrey80,
    tertiary = Pink80,
    // Every text/icon-on-surface role spelled out explicitly and kept near-white — this is
    // the actual fix for "text disappears in dark mode": we no longer lean on the library's
    // own defaults for these roles at all.
    background = Color(0xFF121212),
    onBackground = Color(0xFFF5F5F5),
    surface = Color(0xFF1E1E1E),
    onSurface = Color(0xFFF5F5F5),
    surfaceVariant = Color(0xFF2A2A2A),
    onSurfaceVariant = Color(0xFFE0E0E0),
    error = Color(0xFFFF6B6B),
    onError = Color(0xFF121212)
)

private val LightColors = lightColorScheme(
    primary = Purple40,
    secondary = PurpleGrey40,
    tertiary = Pink40
)

@Composable
fun PersonalVaultTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    // Disabled by default: Android's per-device "Material You" dynamic color (derived from the
    // wallpaper) was producing poor text contrast in dark mode on some devices/wallpapers —
    // our own fixed Light/Dark color schemes below are guaranteed to stay readable.
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColors
        else -> LightColors
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
