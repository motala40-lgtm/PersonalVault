package com.example.personalvault.ui.screens

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.example.personalvault.R
import com.example.personalvault.data.Reminder
import com.example.personalvault.data.RepeatType
import com.example.personalvault.util.ReminderScheduler
import com.example.personalvault.viewmodel.VaultViewModel
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReminderListScreen(viewModel: VaultViewModel, onBack: () -> Unit) {
    val context = LocalContext.current
    val reminders by viewModel.reminders.collectAsState()
    var showAddDialog by remember { mutableStateOf(false) }
    val yearlySuffix = stringResource(R.string.yearly_suffix)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.nav_reminders)) },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, contentDescription = stringResource(R.string.back)) }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { showAddDialog = true }) {
                Icon(Icons.Default.Add, contentDescription = stringResource(R.string.new_reminder))
            }
        }
    ) { padding ->
        LazyColumn(Modifier.padding(padding).padding(horizontal = 12.dp)) {
            if (!ReminderScheduler.canScheduleExact(context)) {
                item {
                    Card(
                        Modifier.fillMaxWidth().padding(vertical = 6.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)
                    ) {
                        Column(Modifier.padding(12.dp)) {
                            Text(
                                stringResource(R.string.exact_alarm_permission_notice),
                                style = MaterialTheme.typography.bodySmall
                            )
                            Spacer(Modifier.height(8.dp))
                            TextButton(onClick = {
                                val intent = android.content.Intent(android.provider.Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM)
                                context.startActivity(intent)
                            }) {
                                Text(stringResource(R.string.open_settings))
                            }
                        }
                    }
                }
            }
            items(reminders, key = { it.id }) { reminder ->
                val dateStr = remember(reminder.dateTimeMillis) {
                    SimpleDateFormat("yyyy/MM/dd HH:mm", Locale.getDefault()).format(Date(reminder.dateTimeMillis))
                }
                Card(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                    Row(
                        Modifier.padding(12.dp).fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
                    ) {
                        Column {
                            Text(reminder.title, style = MaterialTheme.typography.titleMedium)
                            Text(
                                dateStr + if (reminder.repeatType == RepeatType.YEARLY) yearlySuffix else "",
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                        IconButton(onClick = {
                            ReminderScheduler.cancel(context, reminder)
                            viewModel.deleteReminder(reminder)
                        }) {
                            Icon(Icons.Default.Delete, contentDescription = stringResource(R.string.delete))
                        }
                    }
                }
            }
        }
    }

    if (showAddDialog) {
        AddReminderDialog(
            onDismiss = { showAddDialog = false },
            onCreate = { reminder ->
                viewModel.addReminder(reminder) { savedReminder ->
                    ReminderScheduler.schedule(context, savedReminder)
                }
                showAddDialog = false
            }
        )
    }
}

@Composable
private fun AddReminderDialog(onDismiss: () -> Unit, onCreate: (Reminder) -> Unit) {
    val context = LocalContext.current
    var title by remember { mutableStateOf("") }

    // Date and time are tracked separately and start as "not chosen" (null) — previously this
    // silently defaulted to "now" if the user never opened the picker, which could create a
    // reminder scheduled for the current moment without the user realizing it.
    var selectedYear by remember { mutableStateOf<Int?>(null) }
    var selectedMonth by remember { mutableStateOf<Int?>(null) } // 0-based, matches java.util.Calendar
    var selectedDay by remember { mutableStateOf<Int?>(null) }
    var selectedHour by remember { mutableStateOf<Int?>(null) }
    var selectedMinute by remember { mutableStateOf<Int?>(null) }

    var repeatYearly by remember { mutableStateOf(false) }
    var soundEnabled by remember { mutableStateOf(true) }
    var vibrateEnabled by remember { mutableStateOf(true) }
    var errorText by remember { mutableStateOf<String?>(null) }

    val dateNotSelectedText = stringResource(R.string.date_not_selected)
    val timeNotSelectedText = stringResource(R.string.time_not_selected)
    val missingFieldsError = stringResource(R.string.reminder_missing_fields_error)
    val pastError = stringResource(R.string.reminder_past_error)

    val dateLabel = if (selectedYear != null && selectedMonth != null && selectedDay != null) {
        val cal = Calendar.getInstance().apply { set(selectedYear!!, selectedMonth!!, selectedDay!!) }
        SimpleDateFormat("yyyy/MM/dd", Locale.getDefault()).format(cal.time)
    } else dateNotSelectedText

    val timeLabel = if (selectedHour != null && selectedMinute != null) {
        String.format(Locale.getDefault(), "%02d:%02d", selectedHour, selectedMinute)
    } else timeNotSelectedText

    fun openDatePicker() {
        val now = Calendar.getInstance()
        val dialog = DatePickerDialog(
            context,
            { _, year, month, day ->
                selectedYear = year; selectedMonth = month; selectedDay = day
                errorText = null
            },
            selectedYear ?: now.get(Calendar.YEAR),
            selectedMonth ?: now.get(Calendar.MONTH),
            selectedDay ?: now.get(Calendar.DAY_OF_MONTH)
        )
        // Don't let the user pick a day that's already in the past.
        dialog.datePicker.minDate = now.timeInMillis - 1000L
        dialog.show()
    }

    fun openTimePicker() {
        val now = Calendar.getInstance()
        TimePickerDialog(
            context,
            { _, hour, minute ->
                selectedHour = hour; selectedMinute = minute
                errorText = null
            },
            selectedHour ?: now.get(Calendar.HOUR_OF_DAY),
            selectedMinute ?: now.get(Calendar.MINUTE),
            true
        ).show()
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.new_reminder)) },
        text = {
            Column {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text(stringResource(R.string.event_title_label)) },
                    singleLine = true
                )
                Spacer(Modifier.height(12.dp))

                // Day/month/year picker
                OutlinedButton(onClick = { openDatePicker() }, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Default.CalendarMonth, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("${stringResource(R.string.pick_date)}: $dateLabel")
                }
                Spacer(Modifier.height(8.dp))

                // Hour/minute picker
                OutlinedButton(onClick = { openTimePicker() }, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Default.Schedule, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("${stringResource(R.string.pick_time)}: $timeLabel")
                }

                Spacer(Modifier.height(12.dp))
                Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                    Checkbox(checked = repeatYearly, onCheckedChange = { repeatYearly = it })
                    Text(stringResource(R.string.repeat_yearly))
                }
                Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                    Checkbox(checked = soundEnabled, onCheckedChange = { soundEnabled = it })
                    Text(stringResource(R.string.sound_label))
                }
                Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                    Checkbox(checked = vibrateEnabled, onCheckedChange = { vibrateEnabled = it })
                    Text(stringResource(R.string.vibrate_label))
                }

                errorText?.let {
                    Spacer(Modifier.height(8.dp))
                    Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                if (title.isBlank()) {
                    return@TextButton
                }
                if (selectedYear == null || selectedMonth == null || selectedDay == null ||
                    selectedHour == null || selectedMinute == null
                ) {
                    errorText = missingFieldsError
                    return@TextButton
                }
                val cal = Calendar.getInstance().apply {
                    set(selectedYear!!, selectedMonth!!, selectedDay!!, selectedHour!!, selectedMinute!!, 0)
                    set(Calendar.MILLISECOND, 0)
                }
                if (cal.timeInMillis <= System.currentTimeMillis()) {
                    errorText = pastError
                    return@TextButton
                }
                onCreate(
                    Reminder(
                        title = title,
                        dateTimeMillis = cal.timeInMillis,
                        repeatType = if (repeatYearly) RepeatType.YEARLY else RepeatType.NONE,
                        soundEnabled = soundEnabled,
                        vibrateEnabled = vibrateEnabled
                    )
                )
            }) { Text(stringResource(R.string.save)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
        }
    )
}
