package com.example.personalvault.util

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

/**
 * Note on the storage layer: everything here is written into [EncryptedSharedPreferences],
 * which is itself AES-256 encrypted at rest using a hardware-backed Android Keystore key.
 * The PBKDF2 hashing below is a second, independent layer of defense — it means even if that
 * outer encryption were ever bypassed, a PIN/answer still isn't a single fast SHA-256 lookup
 * away from being recovered.
 */
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

    private const val PBKDF2_ITERATIONS = 150_000
    private const val SALT_LENGTH_BYTES = 16
    private const val KEY_LENGTH_BITS = 256
    private const val NEW_FORMAT_PREFIX = "pbkdf2:"

    private fun prefs(context: Context) = EncryptedSharedPreferences.create(
        context,
        PREFS_NAME,
        MasterKey.Builder(context).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build(),
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )

    private fun toHex(bytes: ByteArray): String = bytes.joinToString("") { "%02x".format(it) }
    private fun fromHex(hex: String): ByteArray = ByteArray(hex.length / 2) {
        hex.substring(it * 2, it * 2 + 2).toInt(16).toByte()
    }

    // Legacy, unsalted — kept ONLY so PINs/answers set before this update still verify.
    // Never used to produce new hashes.
    private fun legacyHash(text: String): String =
        toHex(MessageDigest.getInstance("SHA-256").digest(text.toByteArray()))

    private fun pbkdf2(text: String, salt: ByteArray): String {
        val spec = PBEKeySpec(text.toCharArray(), salt, PBKDF2_ITERATIONS, KEY_LENGTH_BITS)
        val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
        return toHex(factory.generateSecret(spec).encoded)
    }

    /** New format: "pbkdf2:<saltHex>:<hashHex>" — a random salt per call, so identical PINs
     *  never produce the same stored value twice. Exposed so folder-level PINs use the same
     *  scheme as the app-wide PIN. */
    fun hashValue(text: String): String {
        val salt = ByteArray(SALT_LENGTH_BYTES).also { SecureRandom().nextBytes(it) }
        return "$NEW_FORMAT_PREFIX${toHex(salt)}:${pbkdf2(text, salt)}"
    }

    /** Verifies [text] against a hash produced by [hashValue] (new format) or the old bare
     *  SHA-256 hex (legacy format, no salt) — needed since [hashValue] is no longer a pure,
     *  comparable function and old stored hashes can't be regenerated without the original PIN. */
    private fun verifyHash(text: String, stored: String): Boolean {
        if (!stored.startsWith(NEW_FORMAT_PREFIX)) {
            // Legacy bare SHA-256 hex — no salt, so a straight comparison still works.
            return stored == legacyHash(text)
        }
        val parts = stored.removePrefix(NEW_FORMAT_PREFIX).split(":")
        if (parts.size != 2) return false
        val salt = runCatching { fromHex(parts[0]) }.getOrNull() ?: return false
        return pbkdf2(text, salt) == parts[1]
    }

    fun setPin(context: Context, pin: String) {
        prefs(context).edit().putString(KEY_PIN_HASH, hashValue(pin)).apply()
    }

    fun verifyPin(context: Context, pin: String): Boolean {
        val stored = prefs(context).getString(KEY_PIN_HASH, null) ?: return false
        val valid = verifyHash(pin, stored)
        // Opportunistic upgrade: a legacy (unsalted) PIN that just verified successfully gets
        // re-saved in the new salted format, since this is the only moment we ever see the
        // plaintext PIN again.
        if (valid && !stored.startsWith(NEW_FORMAT_PREFIX)) {
            prefs(context).edit().putString(KEY_PIN_HASH, hashValue(pin)).apply()
        }
        return valid
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
            .putString(KEY_SECURITY_ANSWER_HASH, hashValue(answer.trim().lowercase()))
            .apply()
    }

    fun getSecurityQuestion(context: Context): String? = prefs(context).getString(KEY_SECURITY_QUESTION, null)

    fun hasSecurityQuestion(context: Context): Boolean = getSecurityQuestion(context) != null

    fun verifySecurityAnswer(context: Context, answer: String): Boolean {
        val stored = prefs(context).getString(KEY_SECURITY_ANSWER_HASH, null) ?: return false
        val normalized = answer.trim().lowercase()
        val valid = verifyHash(normalized, stored)
        if (valid && !stored.startsWith(NEW_FORMAT_PREFIX)) {
            prefs(context).edit().putString(KEY_SECURITY_ANSWER_HASH, hashValue(normalized)).apply()
        }
        return valid
    }

    // --- Folder-lock recovery: two fixed questions (same pair for every folder), set up
    // once in Settings, used to reset a forgotten *folder* PIN — separate from the app-wide
    // security question above, and separate from the app-wide PIN itself. ---

    fun setFolderRecoveryAnswers(context: Context, petAnswer: String, cityAnswer: String) {
        prefs(context).edit()
            .putString(KEY_FOLDER_RECOVERY_ANSWER_PET, hashValue(petAnswer.trim().lowercase()))
            .putString(KEY_FOLDER_RECOVERY_ANSWER_CITY, hashValue(cityAnswer.trim().lowercase()))
            .apply()
    }

    fun hasFolderRecoverySetup(context: Context): Boolean =
        prefs(context).getString(KEY_FOLDER_RECOVERY_ANSWER_PET, null) != null

    fun verifyFolderRecoveryAnswers(context: Context, petAnswer: String, cityAnswer: String): Boolean {
        val storedPet = prefs(context).getString(KEY_FOLDER_RECOVERY_ANSWER_PET, null) ?: return false
        val storedCity = prefs(context).getString(KEY_FOLDER_RECOVERY_ANSWER_CITY, null) ?: return false
        val pet = petAnswer.trim().lowercase()
        val city = cityAnswer.trim().lowercase()
        val valid = verifyHash(pet, storedPet) && verifyHash(city, storedCity)
        if (valid) {
            val editor = prefs(context).edit()
            if (!storedPet.startsWith(NEW_FORMAT_PREFIX)) editor.putString(KEY_FOLDER_RECOVERY_ANSWER_PET, hashValue(pet))
            if (!storedCity.startsWith(NEW_FORMAT_PREFIX)) editor.putString(KEY_FOLDER_RECOVERY_ANSWER_CITY, hashValue(city))
            editor.apply()
        }
        return valid
    }

    /** Verifies a folder's own PIN against its stored hash — the folder-lock equivalent of
     *  [verifyPin]. Needed because [hashValue] is no longer directly comparable (see above). */
    fun verifyFolderPin(pin: String, storedHash: String): Boolean = verifyHash(pin, storedHash)
}
