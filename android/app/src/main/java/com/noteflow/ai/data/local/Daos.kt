package com.noteflow.ai.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
interface NoteDao {

    @Query("SELECT * FROM notes WHERE trashed = 0 AND archived = 0 ORDER BY pinned DESC, updatedAt DESC")
    fun observeActive(): Flow<List<NoteEntity>>

    @Query("SELECT * FROM notes WHERE trashed = 0 AND archived = 0 AND updatedAt >= :since ORDER BY pinned DESC, updatedAt DESC")
    fun observeRecent(since: Long): Flow<List<NoteEntity>>

    @Query("SELECT * FROM notes WHERE trashed = 0 AND archived = 0 AND favorite = 1 ORDER BY updatedAt DESC")
    fun observeFavorites(): Flow<List<NoteEntity>>

    @Query("SELECT * FROM notes WHERE trashed = 0 AND archived = 0 AND pinned = 1 ORDER BY updatedAt DESC")
    fun observePinned(): Flow<List<NoteEntity>>

    @Query("SELECT * FROM notes WHERE trashed = 0 AND archived = 1 ORDER BY updatedAt DESC")
    fun observeArchived(): Flow<List<NoteEntity>>

    @Query("SELECT * FROM notes WHERE trashed = 1 ORDER BY updatedAt DESC")
    fun observeTrash(): Flow<List<NoteEntity>>

    @Query("SELECT * FROM notes WHERE trashed = 0 AND archived = 0 AND folderId = :folderId ORDER BY pinned DESC, updatedAt DESC")
    fun observeByFolder(folderId: String): Flow<List<NoteEntity>>

    @Query(
        "SELECT n.* FROM notes n INNER JOIN note_tags t ON t.noteId = n.id " +
            "WHERE t.tag = :tag AND n.trashed = 0 AND n.archived = 0 ORDER BY n.pinned DESC, n.updatedAt DESC"
    )
    fun observeByTag(tag: String): Flow<List<NoteEntity>>

    @Query(
        "SELECT * FROM notes WHERE trashed = 0 AND archived = 0 AND " +
            "(title LIKE '%' || :query || '%' ESCAPE '\\' OR content LIKE '%' || :query || '%' ESCAPE '\\') " +
            "ORDER BY pinned DESC, updatedAt DESC"
    )
    fun observeSearch(query: String): Flow<List<NoteEntity>>

    @Query("SELECT * FROM notes WHERE id = :id")
    fun observeById(id: String): Flow<NoteEntity?>

    @Query("SELECT * FROM notes WHERE id = :id")
    suspend fun getById(id: String): NoteEntity?

    @Query("SELECT * FROM notes WHERE id IN (:ids)")
    suspend fun getByIds(ids: List<String>): List<NoteEntity>

    @Query("SELECT * FROM notes")
    suspend fun getAll(): List<NoteEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(note: NoteEntity)

    @Query("UPDATE notes SET title = :title, content = :content, updatedAt = :updatedAt, wordCount = :wordCount, charCount = :charCount, syncState = 'PENDING' WHERE id = :id")
    suspend fun updateContent(id: String, title: String, content: String, updatedAt: Long, wordCount: Int, charCount: Int)

    @Query("UPDATE notes SET pinned = :pinned, updatedAt = :updatedAt, syncState = 'PENDING' WHERE id = :id")
    suspend fun updatePinned(id: String, pinned: Boolean, updatedAt: Long)

    @Query("UPDATE notes SET favorite = :favorite, updatedAt = :updatedAt, syncState = 'PENDING' WHERE id = :id")
    suspend fun updateFavorite(id: String, favorite: Boolean, updatedAt: Long)

    @Query("UPDATE notes SET archived = :archived, updatedAt = :updatedAt, syncState = 'PENDING' WHERE id = :id")
    suspend fun updateArchived(id: String, archived: Boolean, updatedAt: Long)

    @Query("UPDATE notes SET folderId = :folderId, updatedAt = :updatedAt, syncState = 'PENDING' WHERE id = :id")
    suspend fun updateFolder(id: String, folderId: String?, updatedAt: Long)

    @Query("UPDATE notes SET targetWordCount = :target WHERE id = :id")
    suspend fun updateTargetWordCount(id: String, target: Int?)

    @Query("UPDATE notes SET trashed = 1, updatedAt = :updatedAt, syncState = 'PENDING' WHERE id = :id")
    suspend fun trash(id: String, updatedAt: Long)

    @Query("UPDATE notes SET trashed = 0, updatedAt = :updatedAt, syncState = 'PENDING' WHERE id = :id")
    suspend fun restore(id: String, updatedAt: Long)

    @Query("DELETE FROM notes WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("UPDATE notes SET syncState = :state WHERE id = :id")
    suspend fun updateSyncState(id: String, state: String)

    @Query("UPDATE notes SET syncState = 'SYNCED', lastSyncedAt = :syncedAt WHERE id = :id")
    suspend fun markSynced(id: String, syncedAt: Long)

    @Query("UPDATE notes SET folderId = NULL WHERE folderId = :folderId")
    suspend fun unfileFolder(folderId: String)
}

@Dao
interface FolderDao {
    @Query("SELECT * FROM folders ORDER BY sortOrder ASC, name ASC")
    fun observeAll(): Flow<List<FolderEntity>>

    @Query("SELECT * FROM folders WHERE id = :id")
    suspend fun getById(id: String): FolderEntity?

    @Query("SELECT * FROM folders")
    suspend fun getAll(): List<FolderEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(folder: FolderEntity)

    @Query("DELETE FROM folders WHERE id = :id")
    suspend fun deleteById(id: String)
}

@Dao
interface NoteTagDao {
    @Query("SELECT tag FROM note_tags WHERE noteId = :noteId ORDER BY tag ASC")
    suspend fun getTagsForNote(noteId: String): List<String>

    @Query("SELECT tag FROM note_tags WHERE noteId = :noteId ORDER BY tag ASC")
    fun observeTagsForNote(noteId: String): Flow<List<String>>

    @Query("SELECT DISTINCT tag FROM note_tags ORDER BY tag ASC")
    fun observeAllTags(): Flow<List<String>>

    @Query("SELECT tag, COUNT(*) AS count FROM note_tags GROUP BY tag ORDER BY count DESC, tag ASC")
    fun observeTagCounts(): Flow<List<TagCountRow>>

    @Query("DELETE FROM note_tags WHERE noteId = :noteId")
    suspend fun deleteForNote(noteId: String)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAll(tags: List<NoteTagEntity>)

    @Query("DELETE FROM note_tags WHERE tag = :tag")
    suspend fun deleteTagEverywhere(tag: String)

    @Transaction
    suspend fun replaceTags(noteId: String, tags: List<String>) {
        deleteForNote(noteId)
        if (tags.isNotEmpty()) insertAll(tags.map { NoteTagEntity(noteId, it) })
    }
}

data class TagCountRow(val tag: String, val count: Int)

@Dao
interface SyncQueueDao {
    @Query("SELECT * FROM sync_queue ORDER BY seq ASC")
    suspend fun getAll(): List<SyncQueueEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun enqueue(entry: SyncQueueEntity)

    @Query("DELETE FROM sync_queue WHERE seq = :seq")
    suspend fun remove(seq: Long)

    @Query("DELETE FROM sync_queue WHERE entityType = :entityType AND entityId = :entityId")
    suspend fun removeForEntity(entityType: String, entityId: String)

    @Query("SELECT COUNT(*) FROM sync_queue")
    suspend fun pendingCount(): Int
}
