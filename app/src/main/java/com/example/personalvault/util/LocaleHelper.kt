package com.example.personalvault.util

import android.content.Context
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat

/**
 * Applies the user's chosen [AppLanguage] as the app's per-app language, using
 * AppCompatDelegate.setApplicationLocales() rather than manually wrapping a Context's
 * Configuration/Locale.
 *
 * WHY THIS CHANGED FROM A MANUAL attachBaseContext()/Configuration APPROACH: that older
 * technique worked correctly for locally-built/sideloaded debug APKs, but broke for real
 * Google Play Store installs delivered via Android App Bundle. Play's Split Install /
 * Feature Delivery system manages per-language resource delivery at the OS level through
 * the platform's own LocaleManager — a manually-wrapped Configuration context doesn't
 * correctly interact with that delivery path, so language resources that WERE installed on
 * the device could still fail to actually render, silently falling back to whatever the
 * device's raw system locale + the compiled default (untagged `values/`, which is Persian)
 * resolved to — matching the exact "only the device's system language and Persian work"
 * symptom this replaces. AppCompatDelegate's app-language API is the officially recommended,
 * Play-Store-aware mechanism (backed by the real platform LocaleManager on Android 13+, with
 * an AndroidX-managed equivalent on older versions) and does not have this problem.
 */
object LocaleHelper {

    private fun tagFor(language: AppLanguage): String = when (language) {
        AppLanguage.FA -> "fa"
        AppLanguage.EN -> "en"
        AppLanguage.FR -> "fr"
        AppLanguage.DE -> "de"
        AppLanguage.ES -> "es"
        AppLanguage.AR -> "ar"
        AppLanguage.RU -> "ru"
        AppLanguage.ZH -> "zh"
        AppLanguage.HI -> "hi"
        AppLanguage.TR -> "tr"
        AppLanguage.SV -> "sv"
    }

    /** Sets [language] as the app's per-app language for the whole process going forward.
     *  Triggers the same Activity recreation the old approach did. */
    fun apply(language: AppLanguage) {
        AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(tagFor(language)))
    }

    /** Re-applies whichever language is currently stored in [AppPreferences]. Safe to call
     *  on every app/Activity start — setApplicationLocales is a cheap no-op if the requested
     *  locale is already the active one. */
    fun applyStoredLanguage(context: Context) {
        apply(AppPreferences.getLanguage(context))
    }

    /**
     * Returns a Context whose resources resolve strings in the person's stored language,
     * for use OUTSIDE any Activity (notification text built from a BroadcastReceiver/alarm,
     * where there's no Activity for AppCompatDelegate's per-app-language mechanism to apply
     * to). This manual Configuration-wrapping approach is fine for plain resource-string
     * lookups like this — the Play Store app-bundle-delivery problem [apply()] above exists
     * to work around only affects driving a whole Activity's live UI language, not looking up
     * one string's translation from resources already present on the device.
     */
    fun contextForStoredLanguage(context: Context): Context {
        val tag = tagFor(AppPreferences.getLanguage(context))
        val locale = java.util.Locale.forLanguageTag(tag)
        val config = android.content.res.Configuration(context.resources.configuration)
        config.setLocale(locale)
        return context.createConfigurationContext(config)
    }
}

