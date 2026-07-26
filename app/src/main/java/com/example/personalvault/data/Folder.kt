package com.example.personalvault.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "folders")
data class Folder(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val colorHex: String = "#6750A4",
    val iconName: String = "Folder",
    val createdAt: Long = System.currentTimeMillis()
)
