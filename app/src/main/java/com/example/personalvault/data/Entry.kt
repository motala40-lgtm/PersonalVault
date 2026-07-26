package com.example.personalvault.data

import androidx.room.Entity
import androidx.room.PrimaryKey

enum class EntryType { TEXT, IMAGE, FILE, PDF_SCAN }

@Entity(tableName = "entries")
data class Entry(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val folderId: Long,
    val type: EntryType,
    // For TEXT entries this holds the note text.
    // For IMAGE/FILE/PDF_SCAN entries this holds the path of the saved copy on internal storage.
    val content: String,
    val fileName: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val isPinned: Boolean = false,
    val isFavorite: Boolean = false,
    val isDeleted: Boolean = false,
    val deletedAt: Long? = null
)
