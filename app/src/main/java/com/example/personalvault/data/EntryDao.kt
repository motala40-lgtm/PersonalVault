package com.example.personalvault.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface EntryDao {
    // Pinned items first, then by creation time. Deleted (trashed) items are excluded.
    @Query("""
        SELECT * FROM entries
        WHERE folderId = :folderId AND isDeleted = 0
        ORDER BY isPinned DESC, createdAt ASC
    """)
    fun getEntriesForFolder(folderId: Long): Flow<List<Entry>>

    @Query("""
        SELECT * FROM entries
        WHERE isFavorite = 1 AND isDeleted = 0
        ORDER BY createdAt DESC
    """)
    fun getFavorites(): Flow<List<Entry>>

    @Query("""
        SELECT * FROM entries
        WHERE isDeleted = 1
        ORDER BY deletedAt DESC
    """)
    fun getTrash(): Flow<List<Entry>>

    @Query("""
        SELECT * FROM entries
        WHERE isDeleted = 0 AND (
            content LIKE '%' || :query || '%' OR
            fileName LIKE '%' || :query || '%'
        )
        ORDER BY createdAt DESC
    """)
    fun search(query: String): Flow<List<Entry>>

    // Used before deleting, so callers can remove the underlying files first.
    @Query("SELECT * FROM entries WHERE isDeleted = 1 AND deletedAt < :cutoffMillis")
    suspend fun getOldTrash(cutoffMillis: Long): List<Entry>

    @Query("DELETE FROM entries WHERE isDeleted = 1 AND deletedAt < :cutoffMillis")
    suspend fun purgeOldTrash(cutoffMillis: Long)

    // One-shot (non-Flow) version of getEntriesForFolder, used by folder copy/share which
    // need a plain list rather than an ongoing subscription.
    @Query("""
        SELECT * FROM entries
        WHERE folderId = :folderId AND isDeleted = 0
        ORDER BY isPinned DESC, createdAt ASC
    """)
    suspend fun getEntriesForFolderSnapshot(folderId: Long): List<Entry>

    // Used before deleting a folder, so callers can remove the underlying files first
    // (includes trashed entries too, since they still belong to the folder).
    @Query("SELECT * FROM entries WHERE folderId = :folderId")
    suspend fun getAllEntriesForFolderIncludingTrash(folderId: Long): List<Entry>

    @Query("DELETE FROM entries WHERE folderId = :folderId")
    suspend fun deleteAllEntriesForFolder(folderId: Long)

    @Insert
    suspend fun insertEntry(entry: Entry): Long

    @Update
    suspend fun updateEntry(entry: Entry)

    @Delete
    suspend fun deleteEntry(entry: Entry)
}
