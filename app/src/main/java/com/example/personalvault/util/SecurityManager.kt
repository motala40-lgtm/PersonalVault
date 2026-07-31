package com.example.personalvault.util

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import java.security.MessageDigest

object SecurityManager {
    private const val PREFS_NAME = "secure_prefs"
    private const val KEY_PIN_HASH = "pin_hash"
    private const val KEY_LOCK_ENABLED = "lock_enabled"
    private const val KEY_BIOMETRIC_ENABLED = "biometric_enabled"
    private const val KEY_SCREENSHOT_BLOCK = "screenshot_block"
    private const val KEY_SECURITY_QUESTION = "security_question"
    private const val KEY_SECURITY_ANSWER_HASH = "security_answer_hash"
    private const val KEY_FOLDER_RECOVERY_ANSWER_PET = "folder_recovery_answer_pet_hash"
    private const val KEY_FOLDER_RECOVERY_ANSWER_CITY = "folder_recovery_answer_city_hash"

    private fun prefs(context: Context) = EncryptedSharedPreferences.create(
        context,
        PREFS_NAME,
        MasterKey.Builder(context).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build(),
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )

    private fun hash(text: String): String {
        val bytes = MessageDigest.getInstance("SHA-256").digest(text.toByteArray())
        return bytes.joinToString("") { "%02x".format(it) }
    }

    /** Exposed so folder-level PINs can be hashed the same way as the app-wide PIN. */
    fun hashValue(text: String): String = hash(text)

    fun setPin(context: Context, pin: String) {
        prefs(context).edit().putString(KEY_PIN_HASH, hash(pin)).apply()
    }

    fun verifyPin(context: Context, pin: String): Boolean {
        val stored = prefs(context).getString(KEY_PIN_HASH, null) ?: return false
        return stored == hash(pin)
    }

    fun hasPinSet(context: Context): Boolean = prefs(context).getString(KEY_PIN_HASH, null) != null

    fun setLockEnabled(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean(KEY_LOCK_ENABLED, enabled).apply()
    }

    fun isLockEnabled(context: Context): Boolean = prefs(context).getBoolean(KEY_LOCK_ENABLED, false)

    fun setBiometricEnabled(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean(KEY_BIOMETRIC_ENABLED, enabled).apply()
    }

    fun isBiometricEnabled(context: Context): Boolean = prefs(context).getBoolean(KEY_BIOMETRIC_ENABLED, false)

    fun setScreenshotBlockEnabled(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean(KEY_SCREENSHOT_BLOCK, enabled).apply()
    }

    fun isScreenshotBlockEnabled(context: Context): Boolean = prefs(context).getBoolean(KEY_SCREENSHOT_BLOCK, false)

    // --- Forgot-PIN recovery: a security question set once alongside the PIN, used to
    // let the user reset a forgotten PIN without needing a server or email. ---

    fun setSecurityQuestion(context: Context, question: String, answer: String) {
        prefs(context).edit()
            .putString(KEY_SECURITY_QUESTION, question)
            .putString(KEY_SECURITY_ANSWER_HASH, hash(answer.trim().lowercase()))
            .apply()
    }

    fun getSecurityQuestion(context: Context): String? = prefs(context).getString(KEY_SECURITY_QUESTION, null)

    fun hasSecurityQuestion(context: Context): Boolean = getSecurityQuestion(context) != null

    fun verifySecurityAnswer(context: Context, answer: String): Boolean {
        val stored = prefs(context).getString(KEY_SECURITY_ANSWER_HASH, null) ?: return false
        return stored == hash(answer.trim().lowercase())
    }

    // --- Folder-lock recovery: two fixed questions (same pair for every folder), set up
    // once in Settings, used to reset a forgotten *folder* PIN — separate from the app-wide
    // security question above, and separate from the app-wide PIN itself. ---

    fun setFolderRecoveryAnswers(context: Context, petAnswer: String, cityAnswer: String) {
        prefs(context).edit()
            .putString(KEY_FOLDER_RECOVERY_ANSWER_PET, hash(petAnswer.trim().lowercase()))
            .putString(KEY_FOLDER_RECOVERY_ANSWER_CITY, hash(cityAnswer.trim().lowercase()))
            .apply()
    }

    fun hasFolderRecoverySetup(context: Context): Boolean =
        prefs(context).getString(KEY_FOLDER_RECOVERY_ANSWER_PET, null) != null

    fun verifyFolderRecoveryAnswers(context: Context, petAnswer: String, cityAnswer: String): Boolean {
        val storedPet = prefs(context).getString(KEY_FOLDER_RECOVERY_ANSWER_PET, null) ?: return false
        val storedCity = prefs(context).getString(KEY_FOLDER_RECOVERY_ANSWER_CITY, null) ?: return false
        return storedPet == hash(petAnswer.trim().lowercase()) &&
            storedCity == hash(cityAnswer.trim().lowercase())
    }
}
