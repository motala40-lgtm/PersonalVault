package com.example.personalvault.util

import android.content.Context
import android.content.res.Configuration
import java.util.Locale

/**
 * Applies the user's chosen [AppLanguage] to a [Context]'s configuration/locale
 * so that resource lookups (strings.xml) resolve to the right language,
 * regardless of the device's system language.
 */
object LocaleHelper {

    private fun localeFor(language: AppLanguage): Locale = when (language) {
        AppLanguage.FA -> Locale("fa")
        AppLanguage.EN -> Locale("en")
        AppLanguage.FR -> Locale("fr")
        AppLanguage.DE -> Locale("de")
        AppLanguage.ES -> Locale("es")
        AppLanguage.AR -> Locale("ar")
        AppLanguage.RU -> Locale("ru")
        AppLanguage.ZH -> Locale("zh")
        AppLanguage.HI -> Locale("hi")
        AppLanguage.TR -> Locale("tr")
        AppLanguage.SV -> Locale("sv")
    }

    /** Wraps [context] with a configuration locked to [language]. */
    fun wrap(context: Context, language: AppLanguage): Context {
        val locale = localeFor(language)
        Locale.setDefault(locale)
        val config = Configuration(context.resources.configuration)
        config.setLocale(locale)
        config.setLayoutDirection(locale)
        return context.createConfigurationContext(config)
    }

    /** Wraps [context] using the language currently stored in [AppPreferences]. */
    fun applyStoredLanguage(context: Context): Context =
        wrap(context, AppPreferences.getLanguage(context))
}
