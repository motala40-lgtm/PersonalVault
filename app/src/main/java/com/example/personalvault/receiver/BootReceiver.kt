package com.example.personalvault.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.example.personalvault.repository.VaultRepository
import com.example.personalvault.util.ReminderScheduler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            val appContext = context.applicationContext
            val repo = VaultRepository(appContext)
            CoroutineScope(Dispatchers.IO).launch {
                val reminders = repo.getAllReminders().first()
                reminders.filter { !it.isCompleted }.forEach { reminder ->
                    ReminderScheduler.schedule(appContext, reminder)
                }
            }
        }
    }
}
