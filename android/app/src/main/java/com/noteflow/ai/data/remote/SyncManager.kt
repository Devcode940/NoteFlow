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
import com.noteflow.ai.core.common.Result
import com.noteflow.ai.data.local.AppDatabase
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
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withContext
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
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
 *  2. Pull: fetch all remote notes/folders for the signed-in user and apply
 *     only versions newer than the local ones (last-write-wins, local wins
 *     ties — see [SyncMerge]). Older remote data never overwrites newer
 *     local edits.
 *
 * If Supabase is unconfigured or unreachable, sync reports FAILED but the
 * app remains fully usable offline; queued operations are retained.
 */
@Singleton
class SyncManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val db: AppDatabase,
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

    override suspend fun syncNow(): Result<Unit> = withContext(ioDispatcher) {
        val settings = preferencesRepository.settings.first()
        if (!settings.syncEnabled) {
            return@withContext Result.Failure("Sync is disabled in settings")
        }
        val user = authRepository.currentUser.first()
            ?: return@withContext Result.Failure("Sign in to sync with Supabase")
        val token = authRepository.idToken()
            ?: return@withContext Result.Failure("Could not obtain an auth token")
        val supabaseUrl = BuildConfig.SUPABASE_URL
        val anonKey = BuildConfig.SUPABASE_ANON_KEY
        if (supabaseUrl.isBlank() || anonKey.isBlank()) {
            _syncState.value = SyncState.FAILED
            return@withContext Result.Failure(
                "Supabase is not configured. Set noteflow.supabaseUrl and noteflow.supabaseAnonKey."
            )
        }

        _syncState.value = SyncState.SYNCING
        try {
            val api = buildSupabaseApi(supabaseUrl)
            val authorization = "Bearer $token"

            pushQueue(api, anonKey, authorization, user.uid)
            pullRemote(api, anonKey, authorization)

            val now = System.currentTimeMillis()
            _lastSyncAt.value = now
            _syncState.value = SyncState.SYNCED
            Result.Success(Unit)
        } catch (e: Exception) {
            _syncState.value = SyncState.FAILED
            Result.Failure("Sync failed: ${e.message}", e)
        }
    }

    private suspend fun pushQueue(
        api: SupabaseApi,
        anonKey: String,
        authorization: String,
        userId: String
    ) {
        val queue = db.syncQueueDao().getAll()
        for (entry in queue) {
            when (entry.entityType) {
                "note" -> {
                    if (entry.operation == "delete") {
                        api.deleteNote(anonKey, authorization, "eq.${entry.entityId}")
                    } else {
                        val note = noteRepository.getNote(entry.entityId).getOrNull() ?: continue
                        api.upsertNote(anonKey, authorization, note.toDto(userId))
                        noteRepository.markSynced(note.id, System.currentTimeMillis())
                    }
                }
                "folder" -> {
                    if (entry.operation == "delete") {
                        api.deleteFolder(anonKey, authorization, "eq.${entry.entityId}")
                    } else {
                        val folder = folderRepository.getFolder(entry.entityId).getOrNull() ?: continue
                        api.upsertFolder(anonKey, authorization, folder.toDto(userId))
                    }
                }
            }
            db.syncQueueDao().remove(entry.seq)
        }
    }

    private suspend fun pullRemote(api: SupabaseApi, anonKey: String, authorization: String) {
        val remoteNotes = api.fetchNotes(anonKey, authorization)
        for (dto in remoteNotes) {
            val local = noteRepository.getNote(dto.id).getOrNull()
            if (local == null) {
                noteRepository.applyRemoteNote(dto.toDomain())
            } else if (SyncMerge.shouldApplyRemote(local.updatedAt, dto.updatedAt)) {
                noteRepository.applyRemoteNote(dto.toDomain())
            }
            // Else: local is newer (or equal) — keep it; it will be pushed next run.
        }

        val remoteFolders = api.fetchFolders(anonKey, authorization)
        for (dto in remoteFolders) {
            val local = folderRepository.getFolder(dto.id).getOrNull()
            if (local == null || SyncMerge.shouldApplyRemote(local.updatedAt, dto.updatedAt)) {
                folderRepository.applyRemoteFolder(dto.toDomain())
            }
        }
    }

    private fun buildSupabaseApi(baseUrl: String): SupabaseApi {
        val normalized = if (baseUrl.endsWith("/")) baseUrl else "$baseUrl/"
        return Retrofit.Builder()
            .baseUrl(normalized)
            .addConverterFactory(Json { ignoreUnknownKeys = true }.asConverterFactory("application/json".toMediaType()))
            .build()
            .create(SupabaseApi::class.java)
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
        lastSyncedAt = updatedAt
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
        const val UNIQUE_PERIODIC_WORK = "noteflow-sync-periodic"
        const val UNIQUE_ONE_TIME_WORK = "noteflow-sync-once"
    }
}
