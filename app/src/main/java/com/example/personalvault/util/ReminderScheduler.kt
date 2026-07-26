package com.example.personalvault.util

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import com.example.personalvault.data.Reminder
import com.example.personalvault.data.RepeatType
import com.example.personalvault.receiver.ReminderReceiver

object ReminderScheduler {

    /** Whether the app currently holds permission to schedule exact alarms (always true below API 31). */
    fun canScheduleExact(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return true
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        return alarmManager.canScheduleExactAlarms()
    }

    fun schedule(context: Context, reminder: Reminder) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = Intent(context, ReminderReceiver::class.java).apply {
            putExtra("reminder_id", reminder.id)
            putExtra("title", reminder.title)
            putExtra("sound_enabled", reminder.soundEnabled)
            putExtra("vibrate_enabled", reminder.vibrateEnabled)
        }
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            reminder.id.toInt(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val triggerAt = if (reminder.repeatType == RepeatType.YEARLY) {
            nextYearlyOccurrence(reminder.dateTimeMillis)
        } else {
            reminder.dateTimeMillis
        }

        // On Android 12+ the user can revoke "Alarms & reminders" access; if that happens,
        // fall back to an inexact alarm instead of crashing with a SecurityException.
        if (canScheduleExact(context)) {
            alarmManager.setExactAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP,
                triggerAt,
                pendingIntent
            )
        } else {
            alarmManager.setAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP,
                triggerAt,
                pendingIntent
            )
        }
    }

    fun cancel(context: Context, reminder: Reminder) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = Intent(context, ReminderReceiver::class.java)
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            reminder.id.toInt(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        alarmManager.cancel(pendingIntent)
    }

    /** For yearly reminders, if the date already passed this year, roll forward to next year. */
    private fun nextYearlyOccurrence(originalMillis: Long): Long {
        val cal = java.util.Calendar.getInstance().apply { timeInMillis = originalMillis }
        val now = java.util.Calendar.getInstance()
        cal.set(java.util.Calendar.YEAR, now.get(java.util.Calendar.YEAR))
        if (cal.timeInMillis < now.timeInMillis) {
            cal.add(java.util.Calendar.YEAR, 1)
        }
        return cal.timeInMillis
    }
}
