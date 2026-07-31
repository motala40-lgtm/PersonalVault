package com.example.personalvault.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

// Adds the optional per-folder lock columns. Without this migration, upgrading the app
// on a device that already has a database would crash instead of just adding the columns.
val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE folders ADD COLUMN isLocked INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE folders ADD COLUMN pinHash TEXT")
    }
}

// Adds the "contacts" table for the phonebook mini-app.
val MIGRATION_2_3 = object : Migration(2, 3) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS contacts (
                id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                name TEXT NOT NULL,
                phone TEXT,
                phone2 TEXT,
                email TEXT,
                note TEXT,
                isFavorite INTEGER NOT NULL DEFAULT 0,
                createdAt INTEGER NOT NULL
            )
            """.trimIndent()
        )
    }
}

@Database(entities = [Folder::class, Entry::class, Reminder::class, Contact::class], version = 3, exportSchema = false)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun folderDao(): FolderDao
    abstract fun entryDao(): EntryDao
    abstract fun reminderDao(): ReminderDao
    abstract fun contactDao(): ContactDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "personal_vault.db"
                )
                    .addMigrations(MIGRATION_1_2, MIGRATION_2_3)
                    .build()
                INSTANCE = instance
                instance
            }
        }

        /**
         * Closes and drops the cached instance so the *next* [getInstance] call opens a fresh
         * connection. Needed right before overwriting the underlying .db file during a backup
         * restore — without this, the app would keep querying the old (now-replaced) database.
         * The app still needs a restart afterward: anything already holding a reference to the
         * old instance (e.g. an existing ViewModel) would otherwise keep using a closed database.
         */
        fun closeInstance() {
            synchronized(this) {
                INSTANCE?.close()
                INSTANCE = null
            }
        }
    }
}
