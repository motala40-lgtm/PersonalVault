package com.example.personalvault.data

import androidx.room.Entity
import androidx.room.PrimaryKey

enum class RepeatType { NONE, YEARLY }

@Entity(tableName = "reminders")
data class Reminder(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val dateTimeMillis: Long,
    val repeatType: RepeatType = RepeatType.NONE,
    val soundEnabled: Boolean = true,
    val vibrateEnabled: Boolean = true,
    val linkedFolderId: Long? = null,
    val isCompleted: Boolean = false
)
