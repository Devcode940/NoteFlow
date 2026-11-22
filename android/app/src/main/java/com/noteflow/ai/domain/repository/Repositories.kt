package com.noteflow.ai.domain.repository

import com.noteflow.ai.core.common.Result
import com.noteflow.ai.domain.model.AiGeneratorType
import com.noteflow.ai.domain.model.AiMessage
import com.noteflow.ai.domain.model.AiQuickAction
import com.noteflow.ai.domain.model.AppSettings
import com.noteflow.ai.domain.model.ExportFormat
import com.noteflow.ai.domain.model.Folder
import com.noteflow.ai.domain.model.Note
import com.noteflow.ai.domain.model.NoteFilter
import com.noteflow.ai.domain.model.SortBy
import com.noteflow.ai.domain.model.SortOrder
import com.noteflow.ai.domain.model.SyncState
import com.noteflow.ai.domain.model.UserProfile
import kotlinx.coroutines.flow.Flow

/** Local-first note storage. All operations are offline-safe. */
interface NoteRepository {
    fun observeNotes(filter: NoteFilter, sortBy: SortBy, sortOrder: SortOrder, query: String = ""): Flow<List<Note>>
    fun observeNote(id: String): Flow<Note?>
    fun observeAllTags(): Flow<List<String>>
    fun observeTagCounts(): Flow<Map<String, Int>>
    fun observeSyncStates(): Flow<Map<String, SyncState>>

    suspend fun createNote(title: String = "", content: String = "", folderId: String? = null): Result<Note>
    suspend fun updateNote(note: Note): Result<Unit>
    suspend fun updateContent(id: String, title: String, content: String): Result<Unit>
    suspend fun setPinned(id: String, pinned: Boolean): Result<Unit>
    suspend fun setFavorite(id: String, favorite: Boolean): Result<Unit>
    suspend fun setArchived(id: String, archived: Boolean): Result<Unit>
    suspend fun setFolder(id: String, folderId: String?): Result<Unit>
    suspend fun setTags(id: String, tags: List<String>): Result<Unit>
    suspend fun setTargetWordCount(id: String, target: Int?): Result<Unit>

    /** Soft-delete to trash (enables undo). */
    suspend fun trashNote(id: String): Result<Unit>

    /** Restore from trash. */
    suspend fun restoreNote(id: String): Result<Unit>

    /** Permanent deletion. Only valid for trashed notes. */
    suspend fun deleteNotePermanently(id: String): Result<Unit>

    suspend fun duplicateNote(id: String): Result<Note>
    suspend fun searchNotes(query: String): Result<List<Note>>
    suspend fun getNote(id: String): Result<Note?>
    suspend fun getNotesByIds(ids: List<String>): Result<List<Note>>

    /** Applies a remote (Supabase) version during sync, respecting last-write-wins. */
    suspend fun applyRemoteNote(note: Note): Result<Unit>

    /** Marks a note as synced after a successful push. */
    suspend fun markSynced(id: String, syncedAt: Long): Result<Unit>

    suspend fun setSyncState(id: String, state: SyncState): Result<Unit>
}

interface FolderRepository {
    fun observeFolders(): Flow<List<Folder>>
    suspend fun createFolder(name: String, icon: String, colorHex: String): Result<Folder>
    suspend fun updateFolder(folder: Folder): Result<Unit>
    suspend fun deleteFolder(id: String): Result<Unit>
    suspend fun getFolder(id: String): Result<Folder?>
    suspend fun applyRemoteFolder(folder: Folder): Result<Unit>
}

/**
 * AI operations. Every call goes through the secure backend — the Android
 * app never holds or sends a Gemini API key.
 */
interface AiRepository {
    /** Non-streaming chat. [noteContext] is the selected note's content (untrusted, size-limited server-side). */
    suspend fun chat(messages: List<AiMessage>, noteContext: String?, model: String): Result<String>

    /** Streaming chat; [onChunk] receives incremental text. Cancellation cancels the HTTP call. */
    suspend fun chatStream(
        messages: List<AiMessage>,
        noteContext: String?,
        model: String,
        onChunk: (String) -> Unit
    ): Result<String>

    /** One of the 12 quick actions applied to [text] (selected note or selection). */
    suspend fun runQuickAction(
        action: AiQuickAction,
        text: String,
        instructions: String? = null,
        model: String
    ): Result<String>

    /** One of the 4 content generators. */
    suspend fun generate(
        type: AiGeneratorType,
        prompt: String,
        noteContext: String? = null,
        model: String
    ): Result<String>

    suspend fun checkHealth(): Result<Boolean>
}

/** Authentication state and operations (Firebase + Google Sign-In). */
interface AuthRepository {
    val currentUser: Flow<UserProfile?>
    val isFirebaseConfigured: Boolean

    /**
     * Exchanges a Google ID token (obtained via Credential Manager in the
     * UI, which owns the Activity-based account flow) for a Firebase session.
     */
    suspend fun signInWithIdToken(idToken: String): Result<UserProfile>

    suspend fun signOut()

    /** Current Firebase ID token for backend/Supabase authorization, or null when signed out. */
    suspend fun idToken(): String?
}

/** User preferences persisted in DataStore. */
interface PreferencesRepository {
    val settings: Flow<AppSettings>

    suspend fun setThemeMode(mode: com.noteflow.ai.domain.model.ThemeMode)
    suspend fun setDefaultEditorMode(mode: com.noteflow.ai.domain.model.EditorMode)
    suspend fun setAutoSave(enabled: Boolean)
    suspend fun setAutoSaveDelayMs(delayMs: Long)
    suspend fun setWordGoal(goal: Int)
    suspend fun setAiModel(model: String)
    suspend fun setBackendUrl(url: String)
    suspend fun setSyncEnabled(enabled: Boolean)
    suspend fun setDefaultExportFormat(format: ExportFormat)
    suspend fun recordAiUsage()
}

/** Offline-first synchronization engine. */
interface SyncRepository {
    val syncState: Flow<SyncState>
    val lastSyncAt: Flow<Long?>

    /** Pushes queued local changes, then pulls remote changes (last-write-wins). */
    suspend fun syncNow(): Result<Unit>

    /** Schedules background sync (WorkManager). */
    fun scheduleBackgroundSync()
}
