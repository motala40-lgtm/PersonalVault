package com.example.personalvault.util

import android.content.Context
import android.util.Base64
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import java.security.MessageDigest
import java.security.SecureRandom

/** Result of checking a PIN attempt against the stored one, including brute-force lockout state. */
sealed class PinCheckResult {
    object Success : PinCheckResult()
    data class WrongPin(val attemptsRemaining: Int) : PinCheckResult()
    data class LockedOut(val remainingMillis: Long) : PinCheckResult()
}

object SecurityManager {
    private const val PREFS_NAME = "secure_prefs"
    private const val KEY_PIN_HASH = "pin_hash"
    private const val KEY_PIN_SALT = "pin_salt"
    private const val KEY_LOCK_ENABLED = "lock_enabled"
    private const val KEY_BIOMETRIC_ENABLED = "biometric_enabled"
    private const val KEY_SCREENSHOT_BLOCK = "screenshot_block"
    private const val KEY_FAILED_ATTEMPTS = "failed_pin_attempts"
    private const val KEY_LOCKOUT_UNTIL = "pin_lockout_until"

    // After this many wrong attempts in a row, further attempts are blocked for a while.
    // The lockout doubles each time the user keeps failing after that, up to a cap.
    private const val MAX_ATTEMPTS_BEFORE_LOCKOUT = 5
    private const val BASE_LOCKOUT_MILLIS = 30_000L
    private const val MAX_LOCKOUT_MILLIS = 15 * 60_000L
    private const val HASH_ITERATIONS = 10_000

    private fun prefs(context: Context) = EncryptedSharedPreferences.create(
        context,
        PREFS_NAME,
        MasterKey.Builder(context).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build(),
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )

    // Salted + iterated hash (poor-man's PBKDF2, but built only from MessageDigest so it works
    // on every API level this app supports). The salt means two devices with the same PIN don't
    // end up with the same stored hash, and the iteration count slows down offline guessing.
    private fun hash(pin: String, salt: ByteArray): String {
        var data = salt + pin.toByteArray(Charsets.UTF_8)
        val digest = MessageDigest.getInstance("SHA-256")
        repeat(HASH_ITERATIONS) {
            data = digest.digest(data)
        }
        return Base64.encodeToString(data, Base64.NO_WRAP)
    }

    // Old, unsalted single-round hash — kept only so a PIN set by an earlier version of the app
    // still verifies correctly once, after which it's transparently upgraded to the salted form.
    private fun legacyHash(pin: String): String {
        val bytes = MessageDigest.getInstance("SHA-256").digest(pin.toByteArray())
        return bytes.joinToString("") { "%02x".format(it) }
    }

    fun setPin(context: Context, pin: String) {
        val salt = ByteArray(16).also { SecureRandom().nextBytes(it) }
        prefs(context).edit()
            .putString(KEY_PIN_SALT, Base64.encodeToString(salt, Base64.NO_WRAP))
            .putString(KEY_PIN_HASH, hash(pin, salt))
            .putInt(KEY_FAILED_ATTEMPTS, 0)
            .putLong(KEY_LOCKOUT_UNTIL, 0L)
            .apply()
    }

    private fun verifyPinOnly(context: Context, pin: String): Boolean {
        val p = prefs(context)
        val storedHash = p.getString(KEY_PIN_HASH, null) ?: return false
        val saltB64 = p.getString(KEY_PIN_SALT, null)
        return if (saltB64 != null) {
            val salt = Base64.decode(saltB64, Base64.NO_WRAP)
            storedHash == hash(pin, salt)
        } else {
            // No salt stored yet means this PIN was set before this update. Verify against the
            // legacy format once, and if it matches, re-save it in the new salted format.
            val matches = storedHash == legacyHash(pin)
            if (matches) setPin(context, pin)
            matches
        }
    }

    /** Verifies a PIN attempt, enforcing brute-force lockout. Use this from the lock screen UI. */
    fun checkPin(context: Context, pin: String): PinCheckResult {
        val p = prefs(context)
        val now = System.currentTimeMillis()
        val lockoutUntil = p.getLong(KEY_LOCKOUT_UNTIL, 0L)
        if (now < lockoutUntil) {
            return PinCheckResult.LockedOut(lockoutUntil - now)
        }

        if (verifyPinOnly(context, pin)) {
            p.edit().putInt(KEY_FAILED_ATTEMPTS, 0).putLong(KEY_LOCKOUT_UNTIL, 0L).apply()
            return PinCheckResult.Success
        }

        val attempts = p.getInt(KEY_FAILED_ATTEMPTS, 0) + 1
        return if (attempts >= MAX_ATTEMPTS_BEFORE_LOCKOUT) {
            val extraRounds = attempts - MAX_ATTEMPTS_BEFORE_LOCKOUT
            val lockoutDuration = (BASE_LOCKOUT_MILLIS * (1L shl minOf(extraRounds, 5)))
                .coerceAtMost(MAX_LOCKOUT_MILLIS)
            p.edit()
                .putInt(KEY_FAILED_ATTEMPTS, attempts)
                .putLong(KEY_LOCKOUT_UNTIL, now + lockoutDuration)
                .apply()
            PinCheckResult.LockedOut(lockoutDuration)
        } else {
            p.edit().putInt(KEY_FAILED_ATTEMPTS, attempts).apply()
            PinCheckResult.WrongPin(MAX_ATTEMPTS_BEFORE_LOCKOUT - attempts)
        }
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
}
