package com.example.personalvault.util

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationCompat
import com.example.personalvault.R

object NotificationHelper {
    const val CHANNEL_ID = "reminders_channel"

    fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val localizedContext = LocaleHelper.applyStoredLanguage(context)
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            if (manager.getNotificationChannel(CHANNEL_ID) == null) {
                val channel = NotificationChannel(
                    CHANNEL_ID,
                    localizedContext.getString(R.string.nav_reminders),
                    NotificationManager.IMPORTANCE_HIGH
                ).apply {
                    description = localizedContext.getString(R.string.reminder_channel_description)
                    enableVibration(true)
                }
                manager.createNotificationChannel(channel)
            }
        }
    }

    fun buildNotification(
        context: Context,
        title: String,
        soundEnabled: Boolean,
        vibrateEnabled: Boolean
    ): androidx.core.app.NotificationCompat.Builder {
        ensureChannel(context)
        val localizedContext = LocaleHelper.applyStoredLanguage(context)
        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_popup_reminder)
            .setContentTitle(localizedContext.getString(R.string.reminder_notification_title))
            .setContentText(title)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)

        if (soundEnabled) {
            val soundUri: Uri = android.provider.Settings.System.DEFAULT_NOTIFICATION_URI
            builder.setSound(soundUri)
        }
        if (vibrateEnabled) {
            builder.setVibrate(longArrayOf(0, 400, 200, 400))
        }
        return builder
    }
}
