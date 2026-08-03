package com.example.personalvault.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.pdf.PdfDocument
import android.net.Uri
import android.provider.OpenableColumns
import com.example.personalvault.data.Entry
import com.example.personalvault.data.EntryType
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.UUID
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

object FileUtils {

    private fun vaultDir(context: Context): File {
        val dir = File(context.filesDir, "vault_files")
        if (!dir.exists()) dir.mkdirs()
        return dir
    }

    private fun sharesDir(context: Context): File {
        val dir = File(context.cacheDir, "shares")
        if (!dir.exists()) dir.mkdirs()
        return dir
    }

    /**
     * Bundles every non-text entry's file into a single zip in the cache dir, ready to be
     * shared via [android.content.Intent.ACTION_SEND] + FileProvider. Text notes are included
     * too, each written out as its own .txt file, so a folder full of only notes still has
     * something to share. Returns null if the folder has no entries at all.
     *
     * Blocking/IO-heavy — call from a background thread or coroutine, never the main thread.
     */
    fun zipFolderForShare(context: Context, folderName: String, entries: List<Entry>): File? {
        if (entries.isEmpty()) return null

        // Clear out any stale share zips from previous shares before writing a new one.
        sharesDir(context).listFiles()?.forEach { it.delete() }

        val safeName = folderName.ifBlank { "folder" }.replace(Regex("[\\\\/:*?\"<>|]"), "_")
        val zipFile = File(sharesDir(context), "$safeName.zip")
        val usedNames = mutableSetOf<String>()

        fun uniqueName(preferred: String): String {
            var candidate = preferred
            var counter = 1
            while (!usedNames.add(candidate)) {
                val dot = preferred.lastIndexOf('.')
                candidate = if (dot > 0) {
                    "${preferred.substring(0, dot)}_$counter${preferred.substring(dot)}"
                } else {
                    "${preferred}_$counter"
                }
                counter++
            }
            return candidate
        }

        ZipOutputStream(FileOutputStream(zipFile)).use { zip ->
            entries.forEach { entry ->
                if (entry.type == EntryType.TEXT) {
                    val name = uniqueName("note_${entry.id}.txt")
                    zip.putNextEntry(ZipEntry(name))
                    zip.write(entry.content.toByteArray(Charsets.UTF_8))
                    zip.closeEntry()
                } else {
                    val source = File(entry.content)
                    if (source.exists()) {
                        val name = uniqueName(entry.fileName ?: source.name)
                        zip.putNextEntry(ZipEntry(name))
                        FileInputStream(source).use { it.copyTo(zip) }
                        zip.closeEntry()
                    }
                }
            }
        }
        return zipFile
    }

    /**
     * Looks up the real display name (e.g. "vacation.jpg") of a content:// uri.
     * uri.lastPathSegment is NOT reliable for this — for MediaStore/content uris it
     * usually returns a numeric row id, not the actual file name, which is why
     * saved entries were showing numbers instead of real file names.
     */
    fun getDisplayName(context: Context, uri: Uri): String? {
        if (uri.scheme == "content") {
            context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (nameIndex >= 0 && cursor.moveToFirst()) {
                    val name = cursor.getString(nameIndex)
                    if (!name.isNullOrBlank()) return name
                }
            }
        }
        return uri.lastPathSegment
    }

    /** Copies any picked content:// uri (image or generic file) into app-private storage. */
    fun copyUriToInternalStorage(context: Context, uri: Uri, suggestedName: String?): File {
        val extension = suggestedName?.substringAfterLast('.', "") ?: ""
        val name = "${UUID.randomUUID()}${if (extension.isNotEmpty()) ".$extension" else ""}"
        val outFile = File(vaultDir(context), name)
        context.contentResolver.openInputStream(uri)?.use { input ->
            FileOutputStream(outFile).use { output ->
                input.copyTo(output)
            }
        }
        return outFile
    }

    /**
     * Saves a copy of a vault entry's file out to the device's public storage (Downloads for
     * files/PDFs, the Pictures/EasyArchive album for photos, Movies/EasyArchive for videos) —
     * a plain byte-for-byte copy, so quality is identical to the original. Uses MediaStore on
     * Android 10+ (no permission needed) and falls back to a direct file write + media-scanner
     * nudge on older versions. Blocking — call from a background thread/coroutine.
     */
    fun exportEntryToDevice(context: Context, entry: Entry): Boolean {
        if (entry.type == EntryType.TEXT) return false
        val source = File(entry.content)
        if (!source.exists()) return false
        val displayName = entry.fileName ?: source.name
        val extension = displayName.substringAfterLast('.', "").lowercase()
        val mimeType = android.webkit.MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension)
            ?: "application/octet-stream"

        return try {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                val (collection, relativeDir) = when (entry.type) {
                    EntryType.IMAGE -> android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI to
                        (android.os.Environment.DIRECTORY_PICTURES + "/EasyArchive")
                    EntryType.VIDEO -> android.provider.MediaStore.Video.Media.EXTERNAL_CONTENT_URI to
                        (android.os.Environment.DIRECTORY_MOVIES + "/EasyArchive")
                    else -> android.provider.MediaStore.Downloads.EXTERNAL_CONTENT_URI to
                        (android.os.Environment.DIRECTORY_DOWNLOADS + "/EasyArchive")
                }
                val values = android.content.ContentValues().apply {
                    put(android.provider.MediaStore.MediaColumns.DISPLAY_NAME, displayName)
                    put(android.provider.MediaStore.MediaColumns.MIME_TYPE, mimeType)
                    put(android.provider.MediaStore.MediaColumns.RELATIVE_PATH, relativeDir)
                    put(android.provider.MediaStore.MediaColumns.IS_PENDING, 1)
                }
                val uri = context.contentResolver.insert(collection, values) ?: return false
                context.contentResolver.openOutputStream(uri)?.use { out ->
                    FileInputStream(source).use { it.copyTo(out) }
                } ?: return false
                values.clear()
                values.put(android.provider.MediaStore.MediaColumns.IS_PENDING, 0)
                context.contentResolver.update(uri, values, null, null)
            } else {
                @Suppress("DEPRECATION")
                val baseDir = when (entry.type) {
                    EntryType.IMAGE -> android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_PICTURES)
                    EntryType.VIDEO -> android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_MOVIES)
                    else -> android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_DOWNLOADS)
                }
                val targetDir = File(baseDir, "EasyArchive")
                if (!targetDir.exists()) targetDir.mkdirs()
                val destFile = File(targetDir, displayName)
                FileInputStream(source).use { input ->
                    FileOutputStream(destFile).use { output -> input.copyTo(output) }
                }
                android.media.MediaScannerConnection.scanFile(context, arrayOf(destFile.absolutePath), null, null)
            }
            true
        } catch (e: Exception) {
            false
        }
    }

    /** Creates a new empty file inside app-private storage for the camera to write a full-res photo into. */
    fun createImageCaptureFile(context: Context): File {
        return File(vaultDir(context), "scan_${UUID.randomUUID()}.jpg")
    }

    /** Converts a single captured photo into a one-page PDF file saved in app-private storage. */
    fun imageFileToPdf(context: Context, imageFile: File): File {
        val bitmap = BitmapFactory.decodeFile(imageFile.absolutePath)
        val pdfDocument = PdfDocument()

        val pageInfo = PdfDocument.PageInfo.Builder(bitmap.width, bitmap.height, 1).create()
        val page = pdfDocument.startPage(pageInfo)
        page.canvas.drawBitmap(bitmap, 0f, 0f, null)
        pdfDocument.finishPage(page)

        val pdfFile = File(vaultDir(context), "scan_${UUID.randomUUID()}.pdf")
        FileOutputStream(pdfFile).use { pdfDocument.writeTo(it) }
        pdfDocument.close()
        bitmap.recycle()
        // clean up the intermediate jpg, we only keep the pdf
        imageFile.delete()
        return pdfFile
    }
}
