package com.example.personalvault.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "folders")
data class Folder(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val colorHex: String = "#6750A4",
    val iconName: String = "Folder",
    val createdAt: Long = System.currentTimeMillis(),
    // Optional per-folder lock: independent from the app-wide lock. When isLocked is true,
    // pinHash must be set and the folder can only be opened after entering the matching PIN.
    val isLocked: Boolean = false,
    val pinHash: String? = null
)
