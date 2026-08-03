package com.example.personalvault.ui.theme

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import coil.compose.rememberAsyncImagePainter
import com.example.personalvault.util.AppPreferences
import java.io.File

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

/**
 * Shared background wrapper for every main screen (folder list, settings, favorites, trash,
 * reminders, contacts) so they all stay visually consistent with whatever the person picked:
 * a custom photo from their gallery (shown clear/sharp, cropped to fill the screen like a
 * normal phone wallpaper, with a soft scrim so content on top stays readable), one of the
 * pastel accent colors, or plain white — matching the same source of truth
 * ([AppPreferences]) everywhere instead of each screen deciding independently.
 */
@Composable
fun ScreenBackground(isDarkTheme: Boolean, content: @Composable BoxScope.() -> Unit) {
    val context = LocalContext.current
    val accentHex = AppPreferences.getAccentColorHex(context)
    val wallpaperPath = AppPreferences.getCustomWallpaperPath(context)
    val wallpaperFile = wallpaperPath?.let { File(it) }

    Box(modifier = Modifier.fillMaxSize()) {
        when {
            isDarkTheme -> {
                Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background))
            }
            wallpaperFile != null && wallpaperFile.exists() -> {
                Image(
                    painter = rememberAsyncImagePainter(wallpaperFile),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
                Box(modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.22f)))
            }
            else -> {
                Box(modifier = Modifier.fillMaxSize().then(accentScreenBackground(accentHex, isDarkTheme)))
            }
        }
        content()
    }
}
