package com.example.personalvault.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.personalvault.data.Contact
import com.example.personalvault.data.Entry
import com.example.personalvault.data.EntryType
import com.example.personalvault.data.Folder
import com.example.personalvault.data.Reminder
import com.example.personalvault.repository.VaultRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class VaultViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = VaultRepository(application)

    // Tracks how many database writes launched through [launchTracked] are still in flight.
    // Every write in this ViewModel (creating a folder, adding a photo, renaming something,
    // etc.) goes through this instead of a bare fire-and-forget viewModelScope.launch — so
    // that anything that needs a consistent snapshot of the vault (specifically, backup
    // export) can call [awaitPendingWrites] first and be sure nothing recently triggered by
    // the person is still mid-write. Without this, exporting right after quickly adding
    // several items could silently leave the newest one(s) out of the backup.
    private val pendingWrites = java.util.concurrent.atomic.AtomicInteger(0)

    private fun launchTracked(block: suspend () -> Unit) {
        pendingWrites.incrementAndGet()
        viewModelScope.launch {
            try {
                block()
            } finally {
                pendingWrites.decrementAndGet()
            }
        }
    }

    suspend fun awaitPendingWrites() {
        while (pendingWrites.get() > 0) {
            kotlinx.coroutines.delay(20)
        }
    }

    val folders: StateFlow<List<Folder>> = repository.getAllFolders()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /** folderId -> live item count, for the "N items" label on each folder card. */
    val folderItemCounts: StateFlow<Map<Long, Int>> = repository.getFolderItemCounts()
        .map { list -> list.associate { it.folderId to it.count } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyMap())

    val favorites: StateFlow<List<Entry>> = repository.getFavorites()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val trash: StateFlow<List<Entry>> = repository.getTrash()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val reminders: StateFlow<List<Reminder>> = repository.getAllReminders()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val contacts: StateFlow<List<Contact>> = repository.getAllContacts()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery

    private val _searchResults = MutableStateFlow<List<Entry>>(emptyList())
    val searchResults: StateFlow<List<Entry>> = _searchResults

    fun onSearchQueryChanged(query: String) {
        _searchQuery.value = query
        if (query.isBlank()) {
            _searchResults.value = emptyList()
            return
        }
        viewModelScope.launch {
            repository.search(query).collect { _searchResults.value = it }
        }
    }

    // Cached per folderId so repeated calls (e.g. from a screen that forgets to `remember`
    // the result) reuse the same StateFlow/coroutine instead of spawning a new one each
    // time — that duplication was the root cause of the icon-freezing/blinking bug.
    private val entriesByFolder = mutableMapOf<Long, StateFlow<List<Entry>>>()

    fun entriesForFolder(folderId: Long): StateFlow<List<Entry>> =
        entriesByFolder.getOrPut(folderId) {
            repository.getEntriesForFolder(folderId)
                .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
        }

    fun createFolder(name: String, colorHex: String, iconName: String) {
        launchTracked { repository.createFolder(name, colorHex, iconName) }
    }

    fun deleteFolder(folder: Folder) {
        launchTracked { repository.deleteFolder(folder) }
    }

    fun copyFolder(folder: Folder, copyNameSuffix: String) {
        launchTracked { repository.copyFolder(folder, copyNameSuffix) }
    }

    /** Builds a shareable zip of [folder]'s contents in the background, then hands the file (or
     * null if the folder was empty) back to [onReady] on the main thread. */
    fun shareFolder(context: android.content.Context, folder: Folder, onReady: (java.io.File?) -> Unit) {
        viewModelScope.launch {
            val entries = repository.getEntriesSnapshotForFolder(folder.id)
            val zip = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                com.example.personalvault.util.FileUtils.zipFolderForShare(context, folder.name, entries)
            }
            onReady(zip)
        }
    }

    fun updateFolder(folder: Folder) {
        launchTracked { repository.updateFolder(folder) }
    }

    fun addTextEntry(folderId: Long, text: String) {
        if (text.isBlank()) return
        launchTracked { repository.addTextEntry(folderId, text) }
    }

    fun updateTextEntry(entry: Entry, newText: String) {
        if (newText.isBlank()) return
        launchTracked { repository.updateEntry(entry.copy(content = newText)) }
    }

    fun duplicateEntry(entry: Entry) {
        launchTracked { repository.duplicateEntry(entry) }
    }

    fun addFileEntry(folderId: Long, type: EntryType, path: String, fileName: String) {
        launchTracked { repository.addFileEntry(folderId, type, path, fileName) }
    }

    fun togglePin(entry: Entry) {
        launchTracked { repository.togglePin(entry) }
    }

    fun toggleFavorite(entry: Entry) {
        launchTracked { repository.toggleFavorite(entry) }
    }

    fun renameEntry(entry: Entry, newName: String) {
        if (newName.isBlank()) return
        launchTracked { repository.renameEntry(entry, newName) }
    }

    fun moveToTrash(entry: Entry) {
        launchTracked { repository.moveToTrash(entry) }
    }

    fun moveEntriesToTrash(entries: List<Entry>) {
        if (entries.isEmpty()) return
        launchTracked { repository.moveEntriesToTrash(entries) }
    }

    fun restoreFromTrash(entry: Entry) {
        launchTracked { repository.restoreFromTrash(entry) }
    }

    fun deletePermanently(entry: Entry) {
        launchTracked { repository.deleteEntryPermanently(entry) }
    }

    fun addReminder(reminder: Reminder, onCreated: (Reminder) -> Unit) {
        viewModelScope.launch {
            val id = repository.addReminder(reminder)
            onCreated(reminder.copy(id = id))
        }
    }

    fun deleteReminder(reminder: Reminder) {
        launchTracked { repository.deleteReminder(reminder) }
    }

    fun addContact(contact: Contact) {
        if (contact.name.isBlank()) return
        launchTracked { repository.addContact(contact) }
    }

    fun updateContact(contact: Contact) {
        launchTracked { repository.updateContact(contact) }
    }

    fun deleteContact(contact: Contact) {
        launchTracked { repository.deleteContact(contact) }
    }

    fun toggleContactFavorite(contact: Contact) {
        updateContact(contact.copy(isFavorite = !contact.isFavorite))
    }
}
