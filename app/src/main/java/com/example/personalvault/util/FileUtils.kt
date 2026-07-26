package com.example.personalvault.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.pdf.PdfDocument
import android.net.Uri
import java.io.File
import java.io.FileOutputStream
import java.util.UUID

object FileUtils {

    private fun vaultDir(context: Context): File {
        val dir = File(context.filesDir, "vault_files")
        if (!dir.exists()) dir.mkdirs()
        return dir
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
