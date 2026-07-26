package com.example.personalvault.util

import android.content.Context

enum class ThemeMode { SYSTEM, LIGHT, DARK }
enum class AppLanguage { FA, EN }

object AppPreferences {
    private const val PREFS_NAME = "app_prefs"
    private const val KEY_THEME = "theme_mode"
    private const val KEY_LANGUAGE = "app_language"

    private fun prefs(context: Context) = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun getThemeMode(context: Context): ThemeMode =
        ThemeMode.valueOf(prefs(context).getString(KEY_THEME, ThemeMode.SYSTEM.name) ?: ThemeMode.SYSTEM.name)

    fun setThemeMode(context: Context, mode: ThemeMode) {
        prefs(context).edit().putString(KEY_THEME, mode.name).apply()
    }

    fun getLanguage(context: Context): AppLanguage =
        AppLanguage.valueOf(prefs(context).getString(KEY_LANGUAGE, AppLanguage.FA.name) ?: AppLanguage.FA.name)

    fun setLanguage(context: Context, language: AppLanguage) {
        prefs(context).edit().putString(KEY_LANGUAGE, language.name).apply()
    }
}
