package com.example.personalvault.util

import android.content.Context
import android.net.Uri
import com.example.personalvault.data.AppDatabase
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.security.SecureRandom
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import javax.crypto.Cipher
import javax.crypto.CipherInputStream
import javax.crypto.CipherOutputStream
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/**
 * Full-vault backup/restore: bundles the Room database and every attached file (photos,
 * documents, scans) into a single password-encrypted file the person can send anywhere —
 * email, Drive, Telegram Saved Messages, whatever — via the normal Android share sheet, and
 * later restore after reinstalling the app.
 *
 * Container format (after encryption): [4-byte magic "EAB1"] [16-byte salt] [12-byte IV]
 * [AES-256-GCM ciphertext of a plain zip containing personal_vault.db + vault_files/...].
 *
 * Both directions are STREAMED through the cipher in fixed-size chunks (CipherOutputStream /
 * CipherInputStream) rather than reading the whole file into a byte array first — a vault
 * with a couple hundred MB of photos/videos previously meant holding the entire zip AND its
 * ciphertext in RAM simultaneously, which reliably threw OutOfMemoryError. Streaming keeps
 * memory use constant regardless of vault size.
 *
 * This deliberately does NOT use Android Keystore / EncryptedFile: keys backed by Keystore
 * don't survive an uninstall, which would make the backup useless for its actual purpose.
 * Instead the key is derived from the person's own password via PBKDF2, so the file can be
 * decrypted on any device as long as they remember that password.
 */
object BackupManager {

    private const val MAGIC = "EAB1"
    private const val PBKDF2_ITERATIONS = 150_000
    private const val KEY_LENGTH_BITS = 256
    private const val STREAM_BUFFER_SIZE = 64 * 1024

    sealed class RestoreResult {
        object Success : RestoreResult()
        object WrongPassword : RestoreResult()
        object InvalidFile : RestoreResult()
    }

    private fun vaultFilesDir(context: Context) = File(context.filesDir, "vault_files")
    private fun dbFile(context: Context) = context.getDatabasePath("personal_vault.db")

    private fun backupsDir(context: Context): File {
        val dir = File(context.cacheDir, "backups")
        if (!dir.exists()) dir.mkdirs()
        return dir
    }

    private fun deriveKey(password: String, salt: ByteArray): SecretKeySpec {
        val spec = PBEKeySpec(password.toCharArray(), salt, PBKDF2_ITERATIONS, KEY_LENGTH_BITS)
        val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
        return SecretKeySpec(factory.generateSecret(spec).encoded, "AES")
    }

    /** Merges the write-ahead log into the main .db file so a plain file copy is consistent. */
    private fun checkpointDatabase(context: Context) {
        val db = AppDatabase.getInstance(context)
        runCatching {
            db.openHelper.writableDatabase.query("PRAGMA wal_checkpoint(TRUNCATE)", arrayOf<Any>()).close()
        }
    }

    /**
     * Builds the encrypted backup file inside the app's cache dir, ready to be shared.
     * Blocking/IO-heavy — call from a background thread or coroutine, never the main thread.
     */
    fun exportBackup(context: Context, password: String): File {
        checkpointDatabase(context)

        val stagingZip = File(backupsDir(context), "staging_${System.currentTimeMillis()}.zip")
        try {
            ZipOutputStream(FileOutputStream(stagingZip)).use { zip ->
                dbFile(context).takeIf { it.exists() }?.let { db ->
                    zip.putNextEntry(ZipEntry("personal_vault.db"))
                    FileInputStream(db).use { it.copyTo(zip, STREAM_BUFFER_SIZE) }
                    zip.closeEntry()
                }
                vaultFilesDir(context).listFiles()?.forEach { file ->
                    if (file.isFile) {
                        zip.putNextEntry(ZipEntry("vault_files/${file.name}"))
                        FileInputStream(file).use { it.copyTo(zip, STREAM_BUFFER_SIZE) }
                        zip.closeEntry()
                    }
                }
            }

            val salt = ByteArray(16).also { SecureRandom().nextBytes(it) }
            val iv = ByteArray(12).also { SecureRandom().nextBytes(it) }
            val key = deriveKey(password, salt)

            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(128, iv))

            val timestamp = SimpleDateFormat("yyyyMMdd_HHmm", Locale.US).format(Date())
            val outFile = File(backupsDir(context), "EasyArchive_backup_$timestamp.eabackup")
            FileOutputStream(outFile).use { out ->
                out.write(MAGIC.toByteArray(Charsets.US_ASCII))
                out.write(salt)
                out.write(iv)
                // Streams the zip through the cipher in STREAM_BUFFER_SIZE chunks instead of
                // loading it whole — this is the fix for the OutOfMemoryError on larger vaults.
                CipherOutputStream(out, cipher).use { cipherOut ->
                    FileInputStream(stagingZip).use { it.copyTo(cipherOut, STREAM_BUFFER_SIZE) }
                }
            }
            return outFile
        } finally {
            stagingZip.delete()
        }
    }

    /**
     * Decrypts and restores a backup produced by [exportBackup], replacing all current folders,
     * notes, and files. Blocking/IO-heavy — call from a background thread or coroutine.
     *
     * IMPORTANT for callers: after [RestoreResult.Success], the app must be restarted before
     * any database access — anything already holding the old (now-closed) database instance
     * would otherwise crash or silently keep showing pre-restore data.
     */
    fun importBackup(context: Context, uri: Uri, password: String): RestoreResult {
        val stagingPlainZip = File(backupsDir(context), "staging_restore_${System.currentTimeMillis()}.zip")
        try {
            val input = context.contentResolver.openInputStream(uri) ?: return RestoreResult.InvalidFile
            input.use { stream ->
                val header = ByteArray(4 + 16 + 12)
                var total = 0
                while (total < header.size) {
                    val n = stream.read(header, total, header.size - total)
                    if (n == -1) return RestoreResult.InvalidFile
                    total += n
                }
                if (String(header, 0, 4, Charsets.US_ASCII) != MAGIC) return RestoreResult.InvalidFile
                val salt = header.copyOfRange(4, 20)
                val iv = header.copyOfRange(20, 32)
                val key = deriveKey(password, salt)

                val cipher = Cipher.getInstance("AES/GCM/NoPadding")
                cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(128, iv))

                try {
                    // Streamed decrypt, same reasoning as exportBackup — a wrong password
                    // still surfaces as an exception (GCM's auth tag check) once the stream
                    // is fully consumed, just without holding the whole file in RAM first.
                    CipherInputStream(stream, cipher).use { cipherIn ->
                        FileOutputStream(stagingPlainZip).use { out -> cipherIn.copyTo(out, STREAM_BUFFER_SIZE) }
                    }
                } catch (e: Exception) {
                    return RestoreResult.WrongPassword
                }
            }

            AppDatabase.closeInstance()
            // Stale WAL/SHM files from the pre-restore database must not linger next to the
            // restored .db file, or SQLite could try to "recover" using old, mismatched data.
            File(dbFile(context).path + "-wal").delete()
            File(dbFile(context).path + "-shm").delete()

            val filesDir = vaultFilesDir(context)
            filesDir.listFiles()?.forEach { it.delete() }
            if (!filesDir.exists()) filesDir.mkdirs()

            ZipInputStream(FileInputStream(stagingPlainZip)).use { zip ->
                var entry = zip.nextEntry
                while (entry != null) {
                    val outFile = when {
                        entry.name == "personal_vault.db" -> dbFile(context)
                        entry.name.startsWith("vault_files/") -> File(filesDir, entry.name.removePrefix("vault_files/"))
                        else -> null
                    }
                    if (outFile != null) {
                        outFile.parentFile?.mkdirs()
                        FileOutputStream(outFile).use { out -> zip.copyTo(out, STREAM_BUFFER_SIZE) }
                    }
                    zip.closeEntry()
                    entry = zip.nextEntry
                }
            }
            return RestoreResult.Success
        } catch (e: Exception) {
            return RestoreResult.InvalidFile
        } finally {
            stagingPlainZip.delete()
        }
    }
}
