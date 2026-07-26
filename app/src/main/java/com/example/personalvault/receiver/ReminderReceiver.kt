package com.example.personalvault.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationManagerCompat
import com.example.personalvault.R
import com.example.personalvault.data.RepeatType
import com.example.personalvault.repository.VaultRepository
import com.example.personalvault.util.LocaleHelper
import com.example.personalvault.util.NotificationHelper
import com.example.personalvault.util.ReminderScheduler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.Calendar

class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val id = intent.getLongExtra("reminder_id", 0L)
        val defaultTitle = LocaleHelper.applyStoredLanguage(context).getString(R.string.reminder_notification_title)
        val title = intent.getStringExtra("title") ?: defaultTitle
        val soundEnabled = intent.getBooleanExtra("sound_enabled", true)
        val vibrateEnabled = intent.getBooleanExtra("vibrate_enabled", true)

        // Only post the notification if we're actually allowed to (Android 13+ requires the
        // POST_NOTIFICATIONS runtime permission; without this check a denied permission would
        // crash the receiver with a SecurityException).
        if (NotificationManagerCompat.from(context).areNotificationsEnabled()) {
            val notification = NotificationHelper.buildNotification(context, title, soundEnabled, vibrateEnabled).build()
            runCatching { NotificationManagerCompat.from(context).notify(id.toInt(), notification) }
        }

        // Keep the reminder's own state in sync with what just happened:
        // - a yearly reminder needs to be re-armed for next year (an exact alarm only fires once)
        // - a one-time reminder should be marked completed so it never fires again after a reboot
        val pendingResult = goAsync()
        val appContext = context.applicationContext
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val repository = VaultRepository(appContext)
                val reminder = repository.getReminderById(id)
                if (reminder != null) {
                    if (reminder.repeatType == RepeatType.YEARLY) {
                        val nextYear = Calendar.getInstance().apply {
                            timeInMillis = reminder.dateTimeMillis
                            add(Calendar.YEAR, 1)
                        }.timeInMillis
                        val updated = reminder.copy(dateTimeMillis = nextYear)
                        repository.updateReminder(updated)
                        ReminderScheduler.schedule(appContext, updated)
                    } else {
                        repository.updateReminder(reminder.copy(isCompleted = true))
                    }
                }
            } finally {
                pendingResult.finish()
            }
        }
    }
}
