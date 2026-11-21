package com.noteflow.ai.data.repository

import com.noteflow.ai.core.common.Result
import com.noteflow.ai.core.common.TextStats
import com.noteflow.ai.data.local.AppDatabase
import com.noteflow.ai.data.local.NoteEntity
import com.noteflow.ai.data.local.SyncQueueEntity
import com.noteflow.ai.domain.model.Note
import com.noteflow.ai.domain.model.NoteFilter
import com.noteflow.ai.domain.model.NoteFilterType
import com.noteflow.ai.domain.model.SortBy
import com.noteflow.ai.domain.model.SortOrder
import com.noteflow.ai.domain.model.SyncState
import com.noteflow.ai.domain.repository.NoteRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.util.UUID
import javax.inject.Inject
import javax.inject.Named
import javax.inject.Singleton

/**
 * Room-backed note repository. Room is the primary source of truth; every
 * mutation also enqueues a sync operation so changes survive offline use and
 * are pushed later by SyncManager. No database work happens on the main thread.
 */
@Singleton
class NoteRepositoryImpl @Inject constructor(
    private val db: AppDatabase,
    @Named("io") private val ioDispatcher: CoroutineDispatcher
) : NoteRepository {

    private val noteDao = db.noteDao()
    private val tagDao = db.noteTagDao()
    private val syncQueueDao = db.syncQueueDao()

    override fun observeNotes(
        filter: NoteFilter,
        sortBy: SortBy,
        sortOrder: SortOrder,
        query: String
    ): Flow<List<Note>> {
        val entities: Flow<List<NoteEntity>> = when (filter.type) {
            NoteFilterType.ALL -> noteDao.observeActive()
            NoteFilterType.RECENT -> noteDao.observeRecent(System.currentTimeMillis() - RECENT_WINDOW_MS)
            NoteFilterType.FAVORITES -> noteDao.observeFavorites()
            NoteFilterType.PINNED -> noteDao.observePinned()
            NoteFilterType.ARCHIVED -> noteDao.observeArchived()
            NoteFilterType.TRASH -> noteDao.observeTrash()
            NoteFilterType.FOLDER -> noteDao.observeByFolder(filter.folderId.orEmpty())
            NoteFilterType.TAG -> noteDao.observeByTag(filter.tag.orEmpty())
        }
        val searched = if (query.isBlank()) {
            entities
        } else {
            noteDao.observeSearch(escapeLike(query.trim()))
        }
        return searched
            .map { list -> list.map { it.toDomain() } }
            .map { notes -> attachTags(notes) }
            .map { notes -> sortNotes(notes, sortBy, sortOrder) }
            .flowOn(ioDispatcher)
    }

    override fun observeNote(id: String): Flow<Note?> =
        noteDao.observeById(id)
            .map { it?.toDomain() }
            .map { note -> note?.let { attachTags(listOf(it)).first() } }
            .flowOn(ioDispatcher)

    override fun observeAllTags(): Flow<List<String>> = tagDao.observeAllTags().flowOn(ioDispatcher)

    override fun observeTagCounts(): Flow<Map<String, Int>> =
        tagDao.observeTagCounts()
            .map { rows -> rows.associate { it.tag to it.count } }
            .flowOn(ioDispatcher)

    override fun observeSyncStates(): Flow<Map<String, SyncState>> =
        noteDao.observeActive()
            .map { list -> list.associate { it.id to (runCatching { SyncState.valueOf(it.syncState) }.getOrDefault(SyncState.LOCAL_ONLY)) } }
            .flowOn(ioDispatcher)

    override suspend fun createNote(title: String, content: String, folderId: String?): Result<Note> =
        runCatchingIo {
            val now = System.currentTimeMillis()
            val note = Note(
                id = UUID.randomUUID().toString(),
                title = title,
                content = content,
                folderId = folderId,
                tags = emptyList(),
                createdAt = now,
                updatedAt = now,
                wordCount = TextStats.countWords(content),
                charCount = TextStats.countChars(content),
                syncState = SyncState.PENDING
            )
            noteDao.upsert(NoteEntity.fromDomain(note))
            enqueueSync("note", note.id, "upsert")
            note
        }

    override suspend fun updateNote(note: Note): Result<Unit> = runCatchingIo {
        val updated = note.copy(
            updatedAt = System.currentTimeMillis(),
            wordCount = TextStats.countWords(note.content),
            charCount = TextStats.countChars(note.content),
            syncState = SyncState.PENDING
        )
        noteDao.upsert(NoteEntity.fromDomain(updated))
        tagDao.replaceTags(updated.id, normalizeTags(updated.tags))
        enqueueSync("note", updated.id, "upsert")
        Unit
    }

    override suspend fun updateContent(id: String, title: String, content: String): Result<Unit> =
        runCatchingIo {
            noteDao.updateContent(
                id = id,
                title = title,
                content = content,
                updatedAt = System.currentTimeMillis(),
                wordCount = TextStats.countWords(content),
                charCount = TextStats.countChars(content)
            )
            enqueueSync("note", id, "upsert")
            Unit
        }

    override suspend fun setPinned(id: String, pinned: Boolean): Result<Unit> = runCatchingIo {
        noteDao.updatePinned(id, pinned, System.currentTimeMillis())
        enqueueSync("note", id, "upsert")
        Unit
    }

    override suspend fun setFavorite(id: String, favorite: Boolean): Result<Unit> = runCatchingIo {
        noteDao.updateFavorite(id, favorite, System.currentTimeMillis())
        enqueueSync("note", id, "upsert")
        Unit
    }

    override suspend fun setArchived(id: String, archived: Boolean): Result<Unit> = runCatchingIo {
        noteDao.updateArchived(id, archived, System.currentTimeMillis())
        enqueueSync("note", id, "upsert")
        Unit
    }

    override suspend fun setFolder(id: String, folderId: String?): Result<Unit> = runCatchingIo {
        noteDao.updateFolder(id, folderId, System.currentTimeMillis())
        enqueueSync("note", id, "upsert")
        Unit
    }

    override suspend fun setTags(id: String, tags: List<String>): Result<Unit> = runCatchingIo {
        tagDao.replaceTags(id, normalizeTags(tags))
        enqueueSync("note", id, "upsert")
        Unit
    }

    override suspend fun setTargetWordCount(id: String, target: Int?): Result<Unit> = runCatchingIo {
        noteDao.updateTargetWordCount(id, target)
        Unit
    }

    override suspend fun trashNote(id: String): Result<Unit> = runCatchingIo {
        noteDao.trash(id, System.currentTimeMillis())
        enqueueSync("note", id, "upsert")
        Unit
    }

    override suspend fun restoreNote(id: String): Result<Unit> = runCatchingIo {
        noteDao.restore(id, System.currentTimeMillis())
        enqueueSync("note", id, "upsert")
        Unit
    }

    override suspend fun deleteNotePermanently(id: String): Result<Unit> = runCatchingIo {
        noteDao.deleteById(id)
        enqueueSync("note", id, "delete")
        Unit
    }

    override suspend fun duplicateNote(id: String): Result<Note> = runCatchingIo {
        val source = noteDao.getById(id) ?: throw NoSuchElementException("Note not found: $id")
        val now = System.currentTimeMillis()
        val copy = source.copy(
            id = UUID.randomUUID().toString(),
            title = (source.title.ifBlank { "Untitled" }) + " copy",
            createdAt = now,
            updatedAt = now,
            pinned = false,
            syncState = SyncState.PENDING.name,
            lastSyncedAt = null
        )
        noteDao.upsert(copy)
        tagDao.replaceTags(copy.id, tagDao.getTagsForNote(id))
        enqueueSync("note", copy.id, "upsert")
        copy.toDomain(tagDao.getTagsForNote(copy.id))
    }

    override suspend fun searchNotes(query: String): Result<List<Note>> = runCatchingIo {
        val entities = noteDao.observeSearch(escapeLike(query)).let { flow ->
            // One-shot read: collect the first emission from the search flow.
            kotlinx.coroutines.flow.first(flow)
        }
        attachTags(entities.map { it.toDomain() })
    }

    override suspend fun getNote(id: String): Result<Note?> = runCatchingIo {
        noteDao.getById(id)?.let { attachTags(listOf(it.toDomain())).first() }
    }

    override suspend fun getNotesByIds(ids: List<String>): Result<List<Note>> = runCatchingIo {
        if (ids.isEmpty()) emptyList() else attachTags(noteDao.getByIds(ids).map { it.toDomain() })
    }

    override suspend fun applyRemoteNote(note: Note): Result<Unit> = runCatchingIo {
        noteDao.upsert(NoteEntity.fromDomain(note.copy(syncState = SyncState.SYNCED)))
        tagDao.replaceTags(note.id, normalizeTags(note.tags))
        Unit
    }

    override suspend fun markSynced(id: String, syncedAt: Long): Result<Unit> = runCatchingIo {
        noteDao.markSynced(id, syncedAt)
        Unit
    }

    override suspend fun setSyncState(id: String, state: SyncState): Result<Unit> = runCatchingIo {
        noteDao.updateSyncState(id, state.name)
        Unit
    }

    // -- internals -----------------------------------------------------------

    private suspend fun attachTags(notes: List<Note>): List<Note> {
        if (notes.isEmpty()) return notes
        return notes.map { note ->
            val tags = tagDao.getTagsForNote(note.id)
            if (tags.isEmpty()) note else note.copy(tags = tags)
        }
    }

    private fun sortNotes(notes: List<Note>, sortBy: SortBy, sortOrder: SortOrder): List<Note> {
        val comparator = when (sortBy) {
            SortBy.UPDATED_AT -> compareBy<Note> { it.updatedAt }
            SortBy.CREATED_AT -> compareBy<Note> { it.createdAt }
            SortBy.TITLE -> compareBy<Note, String>(String.CASE_INSENSITIVE_ORDER) { it.title.ifBlank { "Untitled" } }
            SortBy.CUSTOM -> compareBy<Note> { it.sortOrder }
        }
        val ordered = if (sortOrder == SortOrder.DESC) notes.sortedWith(comparator.reversed()) else notes.sortedWith(comparator)
        // Pinned notes always float to the top except in the trash/archive views.
        return ordered.sortedByDescending { it.pinned }
    }

    private fun normalizeTags(tags: List<String>): List<String> =
        tags.map { it.trim().lowercase() }
            .filter { it.isNotEmpty() }
            .distinct()
            .take(MAX_TAGS_PER_NOTE)

    private fun escapeLike(query: String): String =
        query.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_")

    private suspend fun enqueueSync(entityType: String, entityId: String, operation: String) {
        syncQueueDao.enqueue(
            SyncQueueEntity(
                entityType = entityType,
                entityId = entityId,
                operation = operation,
                enqueuedAt = System.currentTimeMillis()
            )
        )
    }

    private suspend fun <T> runCatchingIo(block: suspend () -> T): Result<T> =
        withContext(ioDispatcher) {
            try {
                Result.Success(block())
            } catch (e: Exception) {
                Result.Failure("Database operation failed: ${e.message}", e)
            }
        }

    companion object {
        private const val RECENT_WINDOW_MS = 7L * 24 * 60 * 60 * 1000
        private const val MAX_TAGS_PER_NOTE = 20
    }
}
