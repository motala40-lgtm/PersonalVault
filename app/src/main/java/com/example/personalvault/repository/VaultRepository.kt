package com.example.personalvault.repository

import android.content.Context
import com.example.personalvault.data.*
import kotlinx.coroutines.flow.Flow
import java.io.File

class VaultRepository(private val appContext: Context) {
    private val db = AppDatabase.getInstance(appContext)
    private val folderDao = db.folderDao()
    private val entryDao = db.entryDao()
    private val reminderDao = db.reminderDao()

    // Deletes the on-disk file for an entry, if it has one (TEXT entries have no file).
    private fun deleteFileFor(entry: Entry) {
        if (entry.type != EntryType.TEXT) {
            runCatching { File(entry.content).delete() }
        }
    }

    // Folders
    fun getAllFolders(): Flow<List<Folder>> = folderDao.getAllFolders()
    suspend fun createFolder(name: String, colorHex: String, iconName: String) =
        folderDao.insertFolder(Folder(name = name, colorHex = colorHex, iconName = iconName))
    suspend fun updateFolder(folder: Folder) = folderDao.updateFolder(folder)

    // Deleting a folder also deletes every entry (and its file) that belongs to it,
    // including ones already in the trash, so nothing is left orphaned on disk or in the DB.
    suspend fun deleteFolder(folder: Folder) {
        val entries = entryDao.getAllEntriesForFolderIncludingTrash(folder.id)
        entries.forEach { deleteFileFor(it) }
        entryDao.deleteAllEntriesForFolder(folder.id)
        folderDao.deleteFolder(folder)
    }

    // Entries
    fun getEntriesForFolder(folderId: Long): Flow<List<Entry>> = entryDao.getEntriesForFolder(folderId)
    fun getFavorites(): Flow<List<Entry>> = entryDao.getFavorites()
    fun getTrash(): Flow<List<Entry>> = entryDao.getTrash()
    fun search(query: String): Flow<List<Entry>> = entryDao.search(query)

    suspend fun addTextEntry(folderId: Long, text: String) =
        entryDao.insertEntry(Entry(folderId = folderId, type = EntryType.TEXT, content = text))

    suspend fun addFileEntry(folderId: Long, type: EntryType, path: String, fileName: String) =
        entryDao.insertEntry(Entry(folderId = folderId, type = type, content = path, fileName = fileName))

    suspend fun togglePin(entry: Entry) = entryDao.updateEntry(entry.copy(isPinned = !entry.isPinned))
    suspend fun toggleFavorite(entry: Entry) = entryDao.updateEntry(entry.copy(isFavorite = !entry.isFavorite))

    suspend fun renameEntry(entry: Entry, newName: String) =
        entryDao.updateEntry(entry.copy(fileName = newName))

    suspend fun moveToTrash(entry: Entry) =
        entryDao.updateEntry(entry.copy(isDeleted = true, deletedAt = System.currentTimeMillis()))

    // Bulk version used by multi-selection in a folder — moves every given entry to trash at once.
    suspend fun moveEntriesToTrash(entries: List<Entry>) {
        val now = System.currentTimeMillis()
        entries.forEach { entryDao.updateEntry(it.copy(isDeleted = true, deletedAt = now)) }
    }

    suspend fun restoreFromTrash(entry: Entry) =
        entryDao.updateEntry(entry.copy(isDeleted = false, deletedAt = null))

    suspend fun deleteEntryPermanently(entry: Entry) {
        deleteFileFor(entry)
        entryDao.deleteEntry(entry)
    }

    suspend fun purgeOldTrash(olderThanDays: Int = 30) {
        val cutoff = System.currentTimeMillis() - (olderThanDays.toLong() * 24 * 60 * 60 * 1000)
        // Delete the files first (DB read), then remove the DB rows in one query,
        // otherwise old trashed files would stay on disk forever.
        val toPurge = entryDao.getOldTrash(cutoff)
        toPurge.forEach { deleteFileFor(it) }
        entryDao.purgeOldTrash(cutoff)
    }

    // Reminders
    fun getAllReminders(): Flow<List<Reminder>> = reminderDao.getAllReminders()
    suspend fun getReminderById(id: Long) = reminderDao.getReminderById(id)
    suspend fun addReminder(reminder: Reminder) = reminderDao.insertReminder(reminder)
    suspend fun updateReminder(reminder: Reminder) = reminderDao.updateReminder(reminder)
    suspend fun deleteReminder(reminder: Reminder) = reminderDao.deleteReminder(reminder)
}
