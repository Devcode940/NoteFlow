package com.noteflow.ai.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters

/**
 * NoteFlow offline-first database.
 *
 * Schema history:
 *  - v1: notes, folders, note_tags, sync_queue.
 *
 * Future schema changes must add an explicit [androidx.room.migration.Migration]
 * and bump [version]; destructive migrations are never used for user data.
 * Room schema exports are written to app/schemas (see ksp arg in build file).
 */
@Database(
    entities = [
        NoteEntity::class,
        FolderEntity::class,
        NoteTagEntity::class,
        SyncQueueEntity::class
    ],
    version = 1,
    exportSchema = true
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun noteDao(): NoteDao
    abstract fun folderDao(): FolderDao
    abstract fun noteTagDao(): NoteTagDao
    abstract fun syncQueueDao(): SyncQueueDao
}
