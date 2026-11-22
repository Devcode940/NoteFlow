package com.noteflow.ai.data.local

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.noteflow.ai.domain.model.Folder
import com.noteflow.ai.domain.model.Note
import com.noteflow.ai.domain.model.SyncState

/** Room entity for notes. Mirrors the domain [Note] 1:1. */
@Entity(
    tableName = "notes",
    indices = [
        Index("folderId"),
        Index("updatedAt"),
        Index("archived"),
        Index("trashed")
    ]
)
data class NoteEntity(
    @PrimaryKey val id: String,
    val title: String,
    val content: String,
    val folderId: String?,
    val pinned: Boolean,
    val favorite: Boolean,
    val archived: Boolean,
    val trashed: Boolean,
    val createdAt: Long,
    val updatedAt: Long,
    val sortOrder: Long,
    val wordCount: Int,
    val charCount: Int,
    val targetWordCount: Int?,
    val syncState: String,
    val lastSyncedAt: Long?
) {
    fun toDomain(tags: List<String> = emptyList()): Note = Note(
        id = id,
        title = title,
        content = content,
        folderId = folderId,
        tags = tags,
        pinned = pinned,
        favorite = favorite,
        archived = archived,
        trashed = trashed,
        createdAt = createdAt,
        updatedAt = updatedAt,
        sortOrder = sortOrder,
        wordCount = wordCount,
        charCount = charCount,
        targetWordCount = targetWordCount,
        syncState = runCatching { SyncState.valueOf(syncState) }.getOrDefault(SyncState.LOCAL_ONLY),
        lastSyncedAt = lastSyncedAt
    )

    companion object {
        fun fromDomain(note: Note): NoteEntity = NoteEntity(
            id = note.id,
            title = note.title,
            content = note.content,
            folderId = note.folderId,
            pinned = note.pinned,
            favorite = note.favorite,
            archived = note.archived,
            trashed = note.trashed,
            createdAt = note.createdAt,
            updatedAt = note.updatedAt,
            sortOrder = note.sortOrder,
            wordCount = note.wordCount,
            charCount = note.charCount,
            targetWordCount = note.targetWordCount,
            syncState = note.syncState.name,
            lastSyncedAt = note.lastSyncedAt
        )
    }
}

/** Room entity for folders. */
@Entity(tableName = "folders")
data class FolderEntity(
    @PrimaryKey val id: String,
    val name: String,
    val icon: String,
    val colorHex: String,
    val sortOrder: Long,
    val createdAt: Long,
    val updatedAt: Long
) {
    fun toDomain(): Folder = Folder(id, name, icon, colorHex, sortOrder, createdAt, updatedAt)

    companion object {
        fun fromDomain(folder: Folder): FolderEntity = FolderEntity(
            id = folder.id,
            name = folder.name,
            icon = folder.icon,
            colorHex = folder.colorHex,
            sortOrder = folder.sortOrder,
            createdAt = folder.createdAt,
            updatedAt = folder.updatedAt
        )
    }
}

/**
 * Many-to-many note <-> tag relation. Tags are normalized (lowercase, trimmed)
 * at the repository boundary so "Work" and "work" are the same tag.
 */
@Entity(
    tableName = "note_tags",
    primaryKeys = ["noteId", "tag"],
    foreignKeys = [
        ForeignKey(
            entity = NoteEntity::class,
            parentColumns = ["id"],
            childColumns = ["noteId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("tag")]
)
data class NoteTagEntity(
    val noteId: String,
    val tag: String
)

/**
 * Outbox queue for offline-first sync. Every local mutation enqueues an
 * operation; SyncManager drains it when online. Operations are idempotent
 * (upserts keyed by note id), so retries and duplicates are safe.
 */
@Entity(tableName = "sync_queue")
data class SyncQueueEntity(
    @PrimaryKey(autoGenerate = true) val seq: Long = 0,
    val entityType: String, // "note" | "folder"
    val entityId: String,
    val operation: String, // "upsert" | "delete"
    val enqueuedAt: Long
)
