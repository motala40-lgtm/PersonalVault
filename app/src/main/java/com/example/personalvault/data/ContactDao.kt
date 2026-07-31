package com.example.personalvault.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface ContactDao {
    // Favorites first, then alphabetical (case-insensitive).
    @Query("SELECT * FROM contacts ORDER BY isFavorite DESC, name COLLATE NOCASE ASC")
    fun getAllContacts(): Flow<List<Contact>>

    @Insert
    suspend fun insertContact(contact: Contact): Long

    @Update
    suspend fun updateContact(contact: Contact)

    @Delete
    suspend fun deleteContact(contact: Contact)
}
