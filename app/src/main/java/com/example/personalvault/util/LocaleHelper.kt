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
    }

    private fun languageForTag(tag: String): AppLanguage =
        AppLanguage.entries.firstOrNull { tagFor(it) == tag } ?: AppLanguage.EN

    /** The language actually in effect right now, per AppCompatDelegate — the real source of
     *  truth, rather than our own stored preference (which apply() also updates, but which
     *  could in principle drift out of sync). Falls back to [AppPreferences]'s stored value
     *  if AppCompatDelegate has nothing set yet (shouldn't normally happen once onCreate has
     *  run ensureDefaultLanguageIfNeverSet at least once). */
    fun currentLanguage(context: Context): AppLanguage {
        val current = AppCompatDelegate.getApplicationLocales()
        return if (!current.isEmpty) languageForTag(current[0]!!.toLanguageTag())
               else AppPreferences.getLanguage(context)
    }

    /** Sets [language] as the app's per-app language for the whole process going forward.
     *  Triggers the same Activity recreation the old approach did. */
    fun apply(language: AppLanguage) {
        AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(tagFor(language)))
    }

    /**
     * Establishes English as the default ONLY if no per-app language has ever been set yet
     * (a genuinely first-ever launch). Returns true if it just did so (meaning an Activity
     * recreate() is now in flight) — the caller should stop doing any further onCreate() work
     * on this soon-to-be-destroyed instance when that happens. Returns false (safe to
     * continue normally) once any language — our default or the person's own pick — is
     * already established.
     */
    fun ensureDefaultLanguageIfNeverSet(): Boolean {
        if (AppCompatDelegate.getApplicationLocales().isEmpty) {
            apply(AppLanguage.EN)
            return true
        }
        return false
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
        val current = AppCompatDelegate.getApplicationLocales()
        val tag = if (!current.isEmpty) current[0]!!.toLanguageTag()
                  else tagFor(AppPreferences.getLanguage(context))
        val locale = java.util.Locale.forLanguageTag(tag)
        val config = android.content.res.Configuration(context.resources.configuration)
        config.setLocale(locale)
        return context.createConfigurationContext(config)
    }
}

