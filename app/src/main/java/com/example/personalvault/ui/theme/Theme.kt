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

// "Deep Blue Trust" design system colors — Bayganikade's own palette (adapted from Easy
// Archive's warm/copper "Warm Security" scheme), meant to feel professional and secure.
val WarmPrimary = Color(0xFF1B3A6B)
val WarmOnPrimary = Color(0xFFFFFFFF)
val WarmPrimaryContainer = Color(0xFF4A7FC9)
val WarmOnPrimaryContainer = Color(0xFF0A1930)
val WarmSecondary = Color(0xFF2E5E8C)
val WarmOnSecondary = Color(0xFFFFFFFF)
val WarmSecondaryContainer = Color(0xFFB8D4F0)
val WarmOnSecondaryContainer = Color(0xFF1B3A6B)
val WarmTertiary = Color(0xFF56606E)
val WarmOnTertiary = Color(0xFFFFFFFF)
val WarmTertiaryContainer = Color(0xFF9AA7B8)
val WarmOnTertiaryContainer = Color(0xFF29323D)
val WarmBackground = Color(0xFFF6F8FC)
val WarmOnBackground = Color(0xFF181C25)
val WarmSurfaceVariant = Color(0xFFE1E6EE)
val WarmOnSurfaceVariant = Color(0xFF3C4655)
val WarmOutline = Color(0xFF6B7A8F)
val WarmError = Color(0xFFBA1A1A)
val WarmOnError = Color(0xFFFFFFFF)
val WarmErrorContainer = Color(0xFFFFDAD6)
val WarmOnErrorContainer = Color(0xFF93000A)

private val LightColors = lightColorScheme(
    primary = WarmPrimary,
    onPrimary = WarmOnPrimary,
    primaryContainer = WarmPrimaryContainer,
    onPrimaryContainer = WarmOnPrimaryContainer,
    secondary = WarmSecondary,
    onSecondary = WarmOnSecondary,
    secondaryContainer = WarmSecondaryContainer,
    onSecondaryContainer = WarmOnSecondaryContainer,
    tertiary = WarmTertiary,
    onTertiary = WarmOnTertiary,
    tertiaryContainer = WarmTertiaryContainer,
    onTertiaryContainer = WarmOnTertiaryContainer,
    background = WarmBackground,
    onBackground = WarmOnBackground,
    surface = WarmBackground,
    onSurface = WarmOnBackground,
    surfaceVariant = WarmSurfaceVariant,
    onSurfaceVariant = WarmOnSurfaceVariant,
    outline = WarmOutline,
    error = WarmError,
    onError = WarmOnError,
    errorContainer = WarmErrorContainer,
    onErrorContainer = WarmOnErrorContainer
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
