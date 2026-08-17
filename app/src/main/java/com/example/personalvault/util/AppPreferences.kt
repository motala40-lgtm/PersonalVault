package com.example.personalvault.util

import android.content.Context

enum class ThemeMode { SYSTEM, LIGHT, DARK }
enum class AppLanguage { FA, EN, FR, DE, ES, AR, RU, ZH, HI, TR, SV }
enum class GridColumns(val count: Int) { ONE(1), TWO(2), THREE(3) }

object AppPreferences {
    private const val PREFS_NAME = "app_prefs"
    private const val KEY_THEME = "theme_mode"
    private const val KEY_LANGUAGE = "app_language"
    private const val KEY_GRID_COLUMNS = "grid_columns"
    private const val KEY_FOLDER_GRID_COLUMNS = "folder_grid_columns"
    private const val KEY_ACCENT_COLOR = "accent_color_hex"
    private const val KEY_HAS_SEEN_ONBOARDING = "has_seen_onboarding"
    private const val KEY_HAS_SEEN_EMPTY_VAULT_PROMPT = "has_seen_empty_vault_prompt"

    private fun prefs(context: Context) = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    /** True once the person has tapped "Get Started" on the first-launch welcome screen. */
    fun hasSeenOnboarding(context: Context): Boolean = prefs(context).getBoolean(KEY_HAS_SEEN_ONBOARDING, false)

    fun setHasSeenOnboarding(context: Context, seen: Boolean) {
        prefs(context).edit().putBoolean(KEY_HAS_SEEN_ONBOARDING, seen).apply()
    }

    /** True once the person has dismissed (or acted on) the one-time "vault is empty — do you
     *  have a backup?" prompt shown on the folder list. Prevents it from nagging on every
     *  launch once someone has genuinely started using an empty vault on purpose. */
    fun hasSeenEmptyVaultPrompt(context: Context): Boolean =
        prefs(context).getBoolean(KEY_HAS_SEEN_EMPTY_VAULT_PROMPT, false)

    fun setHasSeenEmptyVaultPrompt(context: Context, seen: Boolean) {
        prefs(context).edit().putBoolean(KEY_HAS_SEEN_EMPTY_VAULT_PROMPT, seen).apply()
    }

    // Null/blank means "White" — no colored background gradient, just the plain theme background.
    fun getAccentColorHex(context: Context): String? {
        val value = prefs(context).getString(KEY_ACCENT_COLOR, null)
        return if (value.isNullOrBlank()) null else value
    }

    fun setAccentColorHex(context: Context, hex: String?) {
        prefs(context).edit().putString(KEY_ACCENT_COLOR, hex).apply()
    }

    private const val KEY_CUSTOM_WALLPAPER_PATH = "custom_wallpaper_path"

    /** Path to a photo (copied into app-private storage) used as the background instead of
     *  the pastel accent gradient. Null means "no custom photo — use the accent color". */
    fun getCustomWallpaperPath(context: Context): String? =
        prefs(context).getString(KEY_CUSTOM_WALLPAPER_PATH, null)

    fun setCustomWallpaperPath(context: Context, path: String?) {
        prefs(context).edit().putString(KEY_CUSTOM_WALLPAPER_PATH, path).apply()
    }

    fun getThemeMode(context: Context): ThemeMode =
        ThemeMode.valueOf(prefs(context).getString(KEY_THEME, ThemeMode.SYSTEM.name) ?: ThemeMode.SYSTEM.name)

    fun setThemeMode(context: Context, mode: ThemeMode) {
        prefs(context).edit().putString(KEY_THEME, mode.name).apply()
    }

    fun getLanguage(context: Context): AppLanguage =
        AppLanguage.valueOf(prefs(context).getString(KEY_LANGUAGE, AppLanguage.EN.name) ?: AppLanguage.EN.name)

    fun setLanguage(context: Context, language: AppLanguage) {
        prefs(context).edit().putString(KEY_LANGUAGE, language.name).apply()
    }

    fun getGridColumns(context: Context): GridColumns =
        GridColumns.valueOf(prefs(context).getString(KEY_GRID_COLUMNS, GridColumns.TWO.name) ?: GridColumns.TWO.name)

    fun setGridColumns(context: Context, columns: GridColumns) {
        prefs(context).edit().putString(KEY_GRID_COLUMNS, columns.name).apply()
    }

    /** Cycles 1 -> 2 -> 3 -> 1 ... used by the grid-size toggle button. */
    fun cycleGridColumns(context: Context): GridColumns {
        val next = when (getGridColumns(context)) {
            GridColumns.ONE -> GridColumns.TWO
            GridColumns.TWO -> GridColumns.THREE
            GridColumns.THREE -> GridColumns.ONE
        }
        setGridColumns(context, next)
        return next
    }

    // Separate from the above — this is specifically for the folder-card grid on the home
    // screen, so someone can have (say) 3-column folders but a 1-column photo view, or
    // vice versa, without the two settings fighting each other.
    fun getFolderGridColumns(context: Context): GridColumns =
        GridColumns.valueOf(prefs(context).getString(KEY_FOLDER_GRID_COLUMNS, GridColumns.TWO.name) ?: GridColumns.TWO.name)

    fun setFolderGridColumns(context: Context, columns: GridColumns) {
        prefs(context).edit().putString(KEY_FOLDER_GRID_COLUMNS, columns.name).apply()
    }

    fun cycleFolderGridColumns(context: Context): GridColumns {
        val next = when (getFolderGridColumns(context)) {
            GridColumns.ONE -> GridColumns.TWO
            GridColumns.TWO -> GridColumns.THREE
            GridColumns.THREE -> GridColumns.ONE
        }
        setFolderGridColumns(context, next)
        return next
    }
}
