package com.noteflow.ai.data.remote

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.noteflow.ai.BuildConfig
import com.noteflow.ai.core.common.Logger
import com.noteflow.ai.core.common.Result
import com.noteflow.ai.domain.SyncMerge
import com.noteflow.ai.domain.model.Folder
import com.noteflow.ai.domain.model.Note
import com.noteflow.ai.domain.model.SyncState
import com.noteflow.ai.domain.repository.AuthRepository
import com.noteflow.ai.domain.repository.FolderRepository
import com.noteflow.ai.domain.repository.NoteRepository
import com.noteflow.ai.domain.repository.PreferencesRepository
import com.noteflow.ai.domain.repository.SyncRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import retrofit2.HttpException
import java.io.IOException
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Named
import javax.inject.Singleton

/**
 * Offline-first sync engine.
 *
 * Algorithm (per sync run):
 *  1. Push: drain the local outbox queue in order. Each operation is an
 *     idempotent upsert/delete keyed by entity id, so retries are safe.
 *  2. Pull: fetch remote notes/folders page by page and apply only versions
 *     newer than the local ones (last-write-wins, local wins ties — see
 *     [SyncMerge]). Older remote data never overwrites newer local edits.
 *
 * [syncNow] is guarded by a mutex so a manual "sync now" and the periodic
 * WorkManager run can never drain the same outbox concurrently.
 *
 * If Supabase is unconfigured or unreachable, sync reports FAILED but the
 * app remains fully usable offline; queued operations are retained.
 */
@Singleton
class SyncManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val db: AppDatabase,
    private val supabaseApi: SupabaseApi,
    private val noteRepository: NoteRepository,
    private val folderRepository: FolderRepository,
    private val authRepository: AuthRepository,
    private val preferencesRepository: PreferencesRepository,
    @Named("io") private val ioDispatcher: CoroutineDispatcher
) : SyncRepository {

    private val _syncState = MutableStateFlow(SyncState.LOCAL_ONLY)
    override val syncState: Flow<SyncState> = _syncState

    private val _lastSyncAt = MutableStateFlow<Long?>(null)
    override val lastSyncAt: Flow<Long?> = _lastSyncAt

    /** Serialises sync runs; one outbox drain at a time (P2-2 fix). */
    private val syncMutex = Mutex()

    override suspend fun syncNow(): Result<Unit> = withContext(ioDispatcher) {
        syncMutex.withLock {
            val settings = preferencesRepository.settings.first()
            if (!settings.syncEnabled) {
                return@withLock Result.Failure("Sync is disabled in settings")
            }
            val user = authRepository.currentUser.first()
                ?: return@withLock Result.Failure("Sign in to sync with Supabase")
            val token = authRepository.idToken()
                ?: return@withLock Result.Failure("Could not obtain an auth token")
            val anonKey = BuildConfig.SUPABASE_ANON_KEY
            if (BuildConfig.SUPABASE_URL.isBlank() || anonKey.isBlank()) {
                _syncState.value = SyncState.FAILED
                return@withLock Result.Failure(
                    "Supabase is not configured. Set noteflow.supabaseUrl and noteflow.supabaseAnonKey."
                )
            }
            if (!BuildConfig.SUPABASE_URL.startsWith("https://")) {
                _syncState.value = SyncState.FAILED
                return@withLock Result.Failure("Supabase URL must use HTTPS")
            }

            _syncState.value = SyncState.SYNCING
            try {
                val authorization = "Bearer $token"
                pushQueue(anonKey, authorization, user.uid)
                pullRemote(anonKey, authorization)

                _lastSyncAt.value = System.currentTimeMillis()
                _syncState.value = SyncState.SYNCED
                Result.Success(Unit)
            } catch (e: CancellationException) {
                throw e
            } catch (e: HttpException) {
                _syncState.value = SyncState.FAILED
                Logger.w(TAG, "Sync HTTP failure", e)
                Result.Failure("Sync failed (server error ${e.code()}). Will retry.", e)
            } catch (e: IOException) {
                _syncState.value = SyncState.FAILED
                Logger.w(TAG, "Sync network failure", e)
                Result.Failure("Sync failed: network unavailable. Will retry.", e)
            } catch (e: Exception) {
                _syncState.value = SyncState.FAILED
                Logger.e(TAG, "Unexpected sync failure", e)
                Result.Failure("Sync failed. Will retry.", e)
            }
        }
    }

    private suspend fun pushQueue(anonKey: String, authorization: String, userId: String) {
        val queue = db.syncQueueDao().getAll()
        for (entry in queue) {
            when (entry.entityType) {
                "note" -> {
                    if (entry.operation == "delete") {
                        supabaseApi.deleteNote(anonKey, authorization, "eq.${entry.entityId}")
                    } else {
                        val note = noteRepository.getNote(entry.entityId).getOrNull() ?: continue
                        supabaseApi.upsertNote(anonKey, authorization, note.toDto(userId))
                        noteRepository.markSynced(note.id, System.currentTimeMillis())
                    }
                }
                "folder" -> {
                    if (entry.operation == "delete") {
                        supabaseApi.deleteFolder(anonKey, authorization, "eq.${entry.entityId}")
                    } else {
                        val folder = folderRepository.getFolder(entry.entityId).getOrNull() ?: continue
                        supabaseApi.upsertFolder(anonKey, authorization, folder.toDto(userId))
                    }
                }
            }
            db.syncQueueDao().remove(entry.seq)
        }
    }

    /**
     * Pulls remote data page by page (P2-3 fix): a user with thousands of
     * notes never materialises the full dataset in a single response.
     */
    private suspend fun pullRemote(anonKey: String, authorization: String) {
        var noteOffset = 0
        while (true) {
            val page = supabaseApi.fetchNotes(
                anonKey, authorization, limit = PAGE_SIZE, offset = noteOffset
            )
            for (dto in page) applyRemoteNote(dto)
            if (page.size < PAGE_SIZE) break
            noteOffset += PAGE_SIZE
        }

        var folderOffset = 0
        while (true) {
            val page = supabaseApi.fetchFolders(
                anonKey, authorization, limit = PAGE_SIZE, offset = folderOffset
            )
            for (dto in page) applyRemoteFolder(dto)
            if (page.size < PAGE_SIZE) break
            folderOffset += PAGE_SIZE
        }
    }

    private suspend fun applyRemoteNote(dto: SupabaseNoteDto) {
        val local = noteRepository.getNote(dto.id).getOrNull()
        if (local != null && !SyncMerge.shouldApplyRemote(local.updatedAt, dto.updatedAt)) {
            // Local is newer or equal — keep it; it will be pushed next run.
            return
        }
        noteRepository.applyRemoteNote(dto.toDomain())
    }

    private suspend fun applyRemoteFolder(dto: SupabaseFolderDto) {
        val local = folderRepository.getFolder(dto.id).getOrNull()
        if (local != null && !SyncMerge.shouldApplyRemote(local.updatedAt, dto.updatedAt)) {
            return
        }
        folderRepository.applyRemoteFolder(dto.toDomain())
    }

    private fun Note.toDto(userId: String): SupabaseNoteDto = SupabaseNoteDto(
        id = id,
        userId = userId,
        title = title,
        content = content,
        folderId = folderId,
        tags = tags,
        pinned = pinned,
        favorite = favorite,
        archived = archived,
        trashed = trashed,
        createdAt = createdAt,
        updatedAt = updatedAt
    )

    private fun Folder.toDto(userId: String): SupabaseFolderDto = SupabaseFolderDto(
        id = id,
        userId = userId,
        name = name,
        icon = icon,
        color = colorHex,
        sortOrder = sortOrder,
        createdAt = createdAt,
        updatedAt = updatedAt
    )

    private fun SupabaseNoteDto.toDomain(): Note = Note(
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
        wordCount = com.noteflow.ai.core.common.TextStats.countWords(content),
        charCount = com.noteflow.ai.core.common.TextStats.countChars(content),
        syncState = SyncState.SYNCED,
        lastSyncedAt = System.currentTimeMillis()
    )

    private fun SupabaseFolderDto.toDomain(): Folder = Folder(
        id = id,
        name = name,
        icon = icon,
        colorHex = color,
        sortOrder = sortOrder,
        createdAt = createdAt,
        updatedAt = updatedAt
    )

    override fun scheduleBackgroundSync() {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()
        val periodic = PeriodicWorkRequestBuilder<SyncWorker>(15, TimeUnit.MINUTES)
            .setConstraints(constraints)
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
            .build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            UNIQUE_PERIODIC_WORK,
            ExistingPeriodicWorkPolicy.KEEP,
            periodic
        )
    }

    /** Enqueues an immediate one-time sync (e.g. after connectivity returns). */
    fun syncSoon() {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()
        val oneTime = OneTimeWorkRequestBuilder<SyncWorker>()
            .setConstraints(constraints)
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(
            UNIQUE_ONE_TIME_WORK,
            ExistingWorkPolicy.REPLACE,
            oneTime
        )
    }

    companion object {
        private const val TAG = "SyncManager"
        private const val PAGE_SIZE = 200
        const val UNIQUE_PERIODIC_WORK = "noteflow-sync-periodic"
        const val UNIQUE_ONE_TIME_WORK = "noteflow-sync-once"
    }
}
