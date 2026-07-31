package com.example.personalvault.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "contacts")
data class Contact(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val phone: String? = null,
    val phone2: String? = null,
    val email: String? = null,
    val note: String? = null,
    val isFavorite: Boolean = false,
    val createdAt: Long = System.currentTimeMillis()
)
