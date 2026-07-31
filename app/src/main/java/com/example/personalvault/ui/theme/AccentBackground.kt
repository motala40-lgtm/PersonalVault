package com.example.personalvault.ui.theme

import androidx.compose.foundation.background
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp

/**
 * Background used on the main screens: a soft gradient tinted with the user's chosen accent
 * color — lighter near the top, the accent color itself near the bottom. Picking "White"
 * (accentHex == null) or being on dark theme falls back to the flat theme background instead,
 * so contrast and readability aren't compromised.
 */
@Composable
fun accentScreenBackground(accentHex: String?, isDarkTheme: Boolean): Modifier {
    val flat = Modifier.background(MaterialTheme.colorScheme.background)
    if (isDarkTheme || accentHex == null) return flat

    val accent = runCatching { Color(android.graphics.Color.parseColor(accentHex)) }.getOrNull()
        ?: return flat
    val top = lerp(accent, Color.White, 0.8f)
    return Modifier.background(Brush.verticalGradient(listOf(top, accent)))
}
