package com.example.personalvault.util

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter

/**
 * Purely local, opt-in crash reporting. This does NOT contact any server automatically —
 * that would contradict the app's "fully offline, nothing ever leaves your device" promise.
 * Instead, an uncaught exception is written to a private file on-device; the next time the
 * app opens, [SettingsScreen] (or wherever the caller hooks in) can offer to let the person
 * review and send that report themselves, via their own email app, if and only if they
 * choose to.
 */
object CrashReporter {
    private const val CRASH_FILE_NAME = "last_crash.txt"

    private fun crashFile(context: Context): File = File(context.filesDir, CRASH_FILE_NAME)

    /** Installs a global handler that records the crash locally, then hands off to whatever
     *  default handler already existed (so the OS still sees and handles the crash normally —
     *  this only adds a side-effect, it never suppresses the actual crash). Call once, as
     *  early as possible in Application/Activity startup. */
    fun install(context: Context) {
        val appContext = context.applicationContext
        val previousHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            runCatching { writeCrashReport(appContext, throwable) }
            previousHandler?.uncaughtException(thread, throwable)
        }
    }

    private fun writeCrashReport(context: Context, throwable: Throwable) {
        val sw = StringWriter()
        throwable.printStackTrace(PrintWriter(sw))
        val versionName = runCatching {
            context.packageManager.getPackageInfo(context.packageName, 0).versionName
        }.getOrNull() ?: "unknown"
        val report = buildString {
            appendLine("Bayganikade crash report (device/app info only — no vault contents)")
            appendLine("App version: $versionName")
            appendLine("Android version: ${Build.VERSION.RELEASE} (SDK ${Build.VERSION.SDK_INT})")
            appendLine("Device: ${Build.MANUFACTURER} ${Build.MODEL}")
            appendLine()
            append(sw.toString())
        }
        crashFile(context).writeText(report)
    }

    /** Returns the last recorded crash report, or null if there isn't one (nothing crashed,
     *  or it was already cleared). */
    fun getPendingCrashReport(context: Context): String? {
        val file = crashFile(context)
        return if (file.exists()) file.readText() else null
    }

    /** Call after the person has either sent or explicitly declined to send the report, so
     *  they aren't asked about the same crash again on the next launch. */
    fun clearPendingCrashReport(context: Context) {
        crashFile(context).delete()
    }

    /** Builds an email intent pre-filled with the crash details, so sending it is a deliberate
     *  action the person takes with their own email app — never an automatic upload. */
    fun buildCrashEmailIntent(reportText: String): Intent {
        return Intent(Intent.ACTION_SENDTO).apply {
            data = Uri.parse("mailto:")
            putExtra(Intent.EXTRA_EMAIL, arrayOf("Newlifetech25@hotmail.com"))
            putExtra(Intent.EXTRA_SUBJECT, "Bayganikade - Crash Report")
            putExtra(Intent.EXTRA_TEXT, reportText)
        }
    }
}
