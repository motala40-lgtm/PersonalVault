package com.example.personalvault.util

import android.content.Context
import android.net.Uri
import com.example.personalvault.data.AppDatabase
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.security.SecureRandom
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import javax.crypto.Cipher
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
 * Container format: [4-byte magic "EAB3"][16-byte salt][8-byte expected plaintext size], then
 * a sequence of independently encrypted CHUNKs, each:
 * [4-byte chunk ciphertext length][12-byte IV][ciphertext+16-byte tag].
 * The plaintext being chunked is a zip containing personal_vault.db + vault_files/...
 *
 * WHY CHUNKED (replaced a whole-file-single-Cipher-session approach): Android's AES/GCM
 * provider (Conscrypt) buffers the ENTIRE input internally regardless of how the calling code
 * feeds it via update(), producing output only at doFinal(). For a multi-hundred-MB vault this
 * alone caused OutOfMemoryError. Encrypting/decrypting in fixed-size independent chunks (each
 * with its own IV and auth tag, each finalized with its own doFinal()) keeps every single
 * Cipher operation bounded to CHUNK_SIZE, so memory use stays flat no matter the vault size.
 *
 * WHY THE EXPECTED-SIZE HEADER FIELD: a `content://` stream for a large file (especially from
 * cloud-backed providers like Drive, or after passing through Telegram/a document picker) can
 * end early — reporting EOF before every byte has actually arrived — without throwing an
 * exception. Without a way to notice this, restore would silently accept a truncated file as
 * "successful", quietly dropping some folders/photos while reporting no problem at all. Storing
 * the exact expected plaintext size up front, and verifying it BEFORE touching any existing
 * data, turns that silent data loss into a clear, safe failure instead — restoring nothing
 * rather than restoring only part of it.
 *
 * This deliberately does NOT use Android Keystore / EncryptedFile: keys backed by Keystore
 * don't survive an uninstall, which would make the backup useless for its actual purpose.
 * Instead the key is derived from the person's own password via PBKDF2, so the file can be
 * decrypted on any device as long as they remember that password.
 */
object BackupManager {

    private const val MAGIC = "EAB3"
    private const val PBKDF2_ITERATIONS = 150_000
    private const val KEY_LENGTH_BITS = 256
    private const val STREAM_BUFFER_SIZE = 64 * 1024
    // Each chunk is encrypted/decrypted in one Cipher.doFinal() call, so this bounds the
    // memory a single crypto operation can use — independent of total vault size.
    private const val CHUNK_SIZE = 4 * 1024 * 1024

    sealed class RestoreResult {
        object Success : RestoreResult()
        object WrongPassword : RestoreResult()
        object InvalidFile : RestoreResult()
        /** The file decrypted correctly (right password, valid format) but didn't contain as
         *  many bytes as the header promised — almost always because the source stream (a
         *  cloud-backed content:// URI, a flaky transfer, etc.) was cut short. The person's
         *  CURRENT vault is left completely untouched when this happens. */
        object IncompleteFile : RestoreResult()
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

    private fun writeInt(out: OutputStream, value: Int) {
        out.write((value ushr 24) and 0xFF)
        out.write((value ushr 16) and 0xFF)
        out.write((value ushr 8) and 0xFF)
        out.write(value and 0xFF)
    }

    private fun writeLong(out: OutputStream, value: Long) {
        for (shift in 56 downTo 0 step 8) {
            out.write(((value ushr shift) and 0xFF).toInt())
        }
    }

    private fun readInt(input: InputStream): Int {
        val b0 = input.read(); val b1 = input.read(); val b2 = input.read(); val b3 = input.read()
        if (b0 == -1 || b1 == -1 || b2 == -1 || b3 == -1) throw java.io.EOFException()
        return (b0 shl 24) or (b1 shl 16) or (b2 shl 8) or b3
    }

    private fun readLong(input: InputStream): Long {
        var result = 0L
        repeat(8) {
            val b = input.read()
            if (b == -1) throw java.io.EOFException()
            result = (result shl 8) or (b.toLong() and 0xFF)
        }
        return result
    }

    private fun readFully(input: InputStream, buffer: ByteArray) {
        var off = 0
        while (off < buffer.size) {
            val n = input.read(buffer, off, buffer.size - off)
            if (n == -1) throw java.io.EOFException()
            off += n
        }
    }

    /** Encrypts [input] into [output] as a sequence of independent [CHUNK_SIZE]-bounded
     *  AES-GCM chunks — see the class doc for why this exists instead of one long Cipher
     *  session. */
    private fun encryptChunked(input: InputStream, output: OutputStream, key: SecretKeySpec) {
        val buffer = ByteArray(CHUNK_SIZE)
        while (true) {
            var filled = 0
            while (filled < buffer.size) {
                val n = input.read(buffer, filled, buffer.size - filled)
                if (n == -1) break
                filled += n
            }
            if (filled == 0) break
            val iv = ByteArray(12).also { SecureRandom().nextBytes(it) }
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(128, iv))
            val ciphertext = cipher.doFinal(buffer, 0, filled)
            writeInt(output, ciphertext.size)
            output.write(iv)
            output.write(ciphertext)
            if (filled < buffer.size) break // that was the last, short chunk
        }
    }

    /** Decrypts the chunk sequence produced by [encryptChunked], returning the total number of
     *  plaintext bytes written. A wrong password or a corrupted/tampered chunk surfaces as an
     *  exception from that chunk's doFinal() (GCM's per-chunk authentication tag check). */
    private fun decryptChunked(input: InputStream, output: OutputStream, key: SecretKeySpec): Long {
        var totalWritten = 0L
        while (true) {
            val length = try {
                readInt(input)
            } catch (e: java.io.EOFException) {
                break
            }
            val iv = ByteArray(12)
            readFully(input, iv)
            val ciphertext = ByteArray(length)
            readFully(input, ciphertext)
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(128, iv))
            val plain = cipher.doFinal(ciphertext)
            output.write(plain)
            totalWritten += plain.size
        }
        return totalWritten
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
            val key = deriveKey(password, salt)
            val expectedSize = stagingZip.length()

            val timestamp = SimpleDateFormat("yyyyMMdd_HHmm", Locale.US).format(Date())
            val outFile = File(backupsDir(context), "EasyArchive_backup_$timestamp.eabackup")
            FileOutputStream(outFile).use { out ->
                out.write(MAGIC.toByteArray(Charsets.US_ASCII))
                out.write(salt)
                writeLong(out, expectedSize)
                FileInputStream(stagingZip).use { input -> encryptChunked(input, out, key) }
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
            var expectedSize = 0L
            input.use { stream ->
                val header = ByteArray(4 + 16)
                readFully(stream, header)
                if (String(header, 0, 4, Charsets.US_ASCII) != MAGIC) return RestoreResult.InvalidFile
                val salt = header.copyOfRange(4, 20)
                expectedSize = readLong(stream)
                val key = deriveKey(password, salt)

                val actualSize = try {
                    FileOutputStream(stagingPlainZip).use { out -> decryptChunked(stream, out, key) }
                } catch (e: Exception) {
                    // A wrong password (or a corrupted/tampered file) fails GCM's authentication
                    // tag check on the very first chunk — exactly the signal we want.
                    return RestoreResult.WrongPassword
                }

                // Verify BEFORE touching any existing data — a source stream that ended early
                // (common with content:// URIs from cloud-backed providers) would otherwise
                // silently restore only part of the vault while still reporting "success".
                if (actualSize != expectedSize) {
                    return RestoreResult.IncompleteFile
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
