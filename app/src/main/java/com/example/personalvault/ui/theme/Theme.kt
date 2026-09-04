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
    onPrimary = Color(0xFF381E72),
    primaryContainer = Color(0xFF4F378B),
    onPrimaryContainer = Color(0xFFEADDFF),
    secondary = PurpleGrey80,
    onSecondary = Color(0xFF332D41),
    secondaryContainer = Color(0xFF4A4458),
    onSecondaryContainer = Color(0xFFE8DEF8),
    tertiary = Pink80,
    onTertiary = Color(0xFF492532),
    tertiaryContainer = Color(0xFF633B48),
    onTertiaryContainer = Color(0xFFFFD8E4),
    // Every text/icon-on-surface role spelled out explicitly and kept near-white — this is
    // the actual fix for "text disappears in dark mode": we no longer lean on the library's
    // own defaults for these roles at all. This also covers button labels specifically:
    // OutlinedButton/TextButton default their text color to colorScheme.primary, which is why
    // onPrimary/primaryContainer/onPrimaryContainer above are now explicit too, not just the
    // background/surface roles — a button whose container itself uses primaryContainer needs
    // onPrimaryContainer to be readable, and that role has no good implicit default here.
    background = Color(0xFF121212),
    onBackground = Color(0xFFF5F5F5),
    surface = Color(0xFF1E1E1E),
    onSurface = Color(0xFFF5F5F5),
    surfaceVariant = Color(0xFF2A2A2A),
    onSurfaceVariant = Color(0xFFE0E0E0),
    outline = Color(0xFF938F99),
    outlineVariant = Color(0xFF49454F),
    error = Color(0xFFFF6B6B),
    errorContainer = Color(0xFF8C1D18),
    onErrorContainer = Color(0xFFFFDAD6),
    onError = Color(0xFF121212)
)

// "Warm Security" design system colors — a warm, cream-toned palette meant to feel like a
// cozy personal archive rather than a cold, clinical vault.
val WarmPrimary = Color(0xFFA04111)
val WarmOnPrimary = Color(0xFFFFFFFF)
val WarmPrimaryContainer = Color(0xFFF27E4B)
val WarmOnPrimaryContainer = Color(0xFF622000)
val WarmSecondary = Color(0xFF306854)
val WarmOnSecondary = Color(0xFFFFFFFF)
val WarmSecondaryContainer = Color(0xFFB1ECD3)
val WarmOnSecondaryContainer = Color(0xFF346D58)
val WarmTertiary = Color(0xFF645E50)
val WarmOnTertiary = Color(0xFFFFFFFF)
val WarmTertiaryContainer = Color(0xFFA59E8E)
val WarmOnTertiaryContainer = Color(0xFF3A3529)
val WarmBackground = Color(0xFFFAF9F6)
val WarmOnBackground = Color(0xFF1A1C1A)
val WarmSurfaceVariant = Color(0xFFE3E2E0)
val WarmOnSurfaceVariant = Color(0xFF56423A)
val WarmOutline = Color(0xFF8A7269)
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
