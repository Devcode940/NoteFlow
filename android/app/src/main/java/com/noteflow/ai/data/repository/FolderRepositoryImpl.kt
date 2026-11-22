package com.noteflow.ai.data.repository

import androidx.room.withTransaction
import com.noteflow.ai.core.common.Logger
import com.noteflow.ai.core.common.Result
import com.noteflow.ai.data.local.AppDatabase
import com.noteflow.ai.data.local.FolderEntity
import com.noteflow.ai.data.local.SyncQueueEntity
import com.noteflow.ai.domain.SyncMerge
import com.noteflow.ai.domain.model.Folder
import com.noteflow.ai.domain.repository.FolderRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.util.UUID
import javax.inject.Inject
import javax.inject.Named
import javax.inject.Singleton

@Singleton
class FolderRepositoryImpl @Inject constructor(
    private val db: AppDatabase,
    @Named("io") private val ioDispatcher: CoroutineDispatcher
) : FolderRepository {

    private val folderDao = db.folderDao()
    private val noteDao = db.noteDao()
    private val syncQueueDao = db.syncQueueDao()

    override fun observeFolders(): Flow<List<Folder>> =
        folderDao.observeAll()
            .map { list -> list.map { it.toDomain() } }
            .flowOn(ioDispatcher)

    override suspend fun createFolder(name: String, icon: String, colorHex: String): Result<Folder> =
        runCatchingIo {
            val now = System.currentTimeMillis()
            val folder = Folder(
                id = UUID.randomUUID().toString(),
                name = name.trim().ifBlank { "Untitled folder" },
                icon = icon,
                colorHex = colorHex,
                sortOrder = now,
                createdAt = now,
                updatedAt = now
            )
            db.withTransaction {
                folderDao.upsert(FolderEntity.fromDomain(folder))
                enqueueSync(folder.id, "upsert")
            }
            folder
        }

    override suspend fun updateFolder(folder: Folder): Result<Unit> = runCatchingIo {
        db.withTransaction {
            folderDao.upsert(
                FolderEntity.fromDomain(folder.copy(updatedAt = System.currentTimeMillis()))
            )
            enqueueSync(folder.id, "upsert")
        }
        Unit
    }

    override suspend fun deleteFolder(id: String): Result<Unit> = runCatchingIo {
        // Notes in the deleted folder become unfiled rather than deleted.
        db.withTransaction {
            noteDao.unfileFolder(id)
            folderDao.deleteById(id)
            enqueueSync(id, "delete")
        }
        Unit
    }

    override suspend fun getFolder(id: String): Result<Folder?> = runCatchingIo {
        folderDao.getById(id)?.toDomain()
    }

    /** Applies a remote folder; last-write-wins, local wins ties. */
    override suspend fun applyRemoteFolder(folder: Folder): Result<Unit> = runCatchingIo {
        val local = folderDao.getById(folder.id)
        if (local != null && !SyncMerge.shouldApplyRemote(local.updatedAt, folder.updatedAt)) {
            return@runCatchingIo
        }
        folderDao.upsert(FolderEntity.fromDomain(folder))
        Unit
    }

    private suspend fun enqueueSync(entityId: String, operation: String) {
        syncQueueDao.enqueue(
            SyncQueueEntity(
                entityType = "folder",
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
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Logger.w(TAG, "Folder operation failed", e)
                Result.Failure("Could not update folders. Please try again.", e)
            }
        }

    companion object {
        private const val TAG = "FolderRepository"
    }
}
