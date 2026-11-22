package com.noteflow.ai.feature.editor

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.noteflow.ai.core.common.Result
import com.noteflow.ai.domain.model.AppSettings
import com.noteflow.ai.domain.model.EditorMode
import com.noteflow.ai.domain.model.ExportFormat
import com.noteflow.ai.domain.model.Folder
import com.noteflow.ai.domain.model.Note
import com.noteflow.ai.domain.model.SortBy
import com.noteflow.ai.domain.model.SortOrder
import com.noteflow.ai.domain.model.SyncState
import com.noteflow.ai.domain.model.ThemeMode
import com.noteflow.ai.domain.model.UserProfile
import com.noteflow.ai.domain.repository.NoteRepository
import com.noteflow.ai.domain.repository.PreferencesRepository
import com.noteflow.ai.domain.usecase.CreateNoteUseCase
import com.noteflow.ai.domain.usecase.SaveNoteContentUseCase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * REGRESSION TEST for P1-1: the editor must persist exactly what the user
 * typed. The old NoteEditorViewModel discarded onTitleChange/onContentChange
 * arguments and saved the repository's ORIGINAL note back to disk, so no
 * edit could ever be persisted.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class NoteEditorViewModelSaveTest {

    private val dispatcher = StandardTestDispatcher()

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun savePersistsEditedTitleAndContent() = runTest(dispatcher) {
        val repo = FakeNoteRepository(
            seededNote = Note(
                id = "n1", title = "Original", content = "original content",
                folderId = null, tags = emptyList(), createdAt = 0, updatedAt = 0
            )
        )
        val viewModel = NoteEditorViewModel(
            noteRepository = repo,
            saveNoteContent = SaveNoteContentUseCase(repo),
            createNoteUseCase = CreateNoteUseCase(repo),
            preferencesRepository = FakePreferencesRepository(),
            savedStateHandle = SavedStateHandle(mapOf("noteId" to "n1"))
        )

        // Wait for the initial load from the repository flow.
        testScheduler.advanceUntilIdle()

        // Simulate the user typing (the old ViewModel threw these values away).
        viewModel.onTitleChange("My edited title")
        viewModel.onContentChange("my edited content")
        viewModel.save()
        testScheduler.advanceUntilIdle()

        assertEquals("My edited title", repo.updatedContent["n1"]?.first)
        assertEquals("my edited content", repo.updatedContent["n1"]?.second)
        assertTrue(repo.updateContentCalled)
    }

    @Test
    fun autoSavePersistsEditsAfterDebounce() = runTest(dispatcher) {
        val repo = FakeNoteRepository(
            seededNote = Note(
                id = "n2", title = "", content = "",
                folderId = null, tags = emptyList(), createdAt = 0, updatedAt = 0
            )
        )
        val viewModel = NoteEditorViewModel(
            noteRepository = repo,
            saveNoteContent = SaveNoteContentUseCase(repo),
            createNoteUseCase = CreateNoteUseCase(repo),
            preferencesRepository = FakePreferencesRepository(autoSaveDelayMs = 100),
            savedStateHandle = SavedStateHandle(mapOf("noteId" to "n2"))
        )
        testScheduler.advanceUntilIdle()

        viewModel.onContentChange("auto saved text")
        // Advance past the debounce window.
        testScheduler.advanceTimeBy(1_000)
        testScheduler.advanceUntilIdle()

        assertEquals("auto saved text", repo.updatedContent["n2"]?.second)
    }

    @Test
    fun newNoteRouteCreatesARealNote() = runTest(dispatcher) {
        val repo = FakeNoteRepository(seededNote = null)
        val viewModel = NoteEditorViewModel(
            noteRepository = repo,
            saveNoteContent = SaveNoteContentUseCase(repo),
            createNoteUseCase = CreateNoteUseCase(repo),
            preferencesRepository = FakePreferencesRepository(),
            savedStateHandle = SavedStateHandle(mapOf("noteId" to "new"))
        )
        testScheduler.advanceUntilIdle()

        // REGRESSION for P1-3: the "new" route must create a real backing note.
        assertEquals(1, repo.createdNotes.size)
        assertEquals(repo.createdNotes.first().id, viewModel.createdNoteId.value)
    }
}

/** In-memory NoteRepository fake recording writes for assertions. */
private class FakeNoteRepository(private val seededNote: Note?) : NoteRepository {
    val updatedContent = mutableMapOf<String, Pair<String, String>>()
    val createdNotes = mutableListOf<Note>()
    var updateContentCalled = false

    private val notes = MutableStateFlow(seededNote?.let { mapOf(it.id to it) } ?: emptyMap())

    override fun observeNotes(filter: com.noteflow.ai.domain.model.NoteFilter, sortBy: SortBy, sortOrder: SortOrder, query: String): Flow<List<Note>> =
        MutableStateFlow(emptyList())

    override fun observeNote(id: String): Flow<Note?> = MutableStateFlow(notes.value[id])

    override fun observeAllTags(): Flow<List<String>> = MutableStateFlow(emptyList())
    override fun observeTagCounts(): Flow<Map<String, Int>> = MutableStateFlow(emptyMap())
    override fun observeSyncStates(): Flow<Map<String, SyncState>> = MutableStateFlow(emptyMap())

    override suspend fun createNote(title: String, content: String, folderId: String?): Result<Note> {
        val note = Note(
            id = "created-${createdNotes.size + 1}", title = title, content = content,
            folderId = folderId, tags = emptyList(), createdAt = 0, updatedAt = 0
        )
        createdNotes += note
        notes.value = notes.value + (note.id to note)
        return Result.Success(note)
    }

    override suspend fun updateNote(note: Note): Result<Unit> {
        notes.value = notes.value + (note.id to note)
        return Result.Success(Unit)
    }

    override suspend fun updateContent(id: String, title: String, content: String): Result<Unit> {
        updateContentCalled = true
        updatedContent[id] = title to content
        val existing = notes.value[id]
        if (existing != null) notes.value = notes.value + (id to existing.copy(title = title, content = content))
        return Result.Success(Unit)
    }

    override suspend fun setPinned(id: String, pinned: Boolean): Result<Unit> = Result.Success(Unit)
    override suspend fun setFavorite(id: String, favorite: Boolean): Result<Unit> = Result.Success(Unit)
    override suspend fun setArchived(id: String, archived: Boolean): Result<Unit> = Result.Success(Unit)
    override suspend fun setFolder(id: String, folderId: String?): Result<Unit> = Result.Success(Unit)
    override suspend fun setTags(id: String, tags: List<String>): Result<Unit> = Result.Success(Unit)
    override suspend fun setTargetWordCount(id: String, target: Int?): Result<Unit> = Result.Success(Unit)
    override suspend fun trashNote(id: String): Result<Unit> = Result.Success(Unit)
    override suspend fun restoreNote(id: String): Result<Unit> = Result.Success(Unit)
    override suspend fun deleteNotePermanently(id: String): Result<Unit> = Result.Success(Unit)

    override suspend fun duplicateNote(id: String): Result<Note> {
        val source = notes.value[id] ?: return Result.Failure("missing")
        val copy = source.copy(id = "copy-$id")
        return Result.Success(copy)
    }

    override suspend fun searchNotes(query: String): Result<List<Note>> = Result.Success(emptyList())
    override suspend fun getNote(id: String): Result<Note?> = Result.Success(notes.value[id])
    override suspend fun getNotesByIds(ids: List<String>): Result<List<Note>> =
        Result.Success(ids.mapNotNull { notes.value[it] })

    override suspend fun applyRemoteNote(note: Note): Result<Unit> {
        notes.value = notes.value + (note.id to note)
        return Result.Success(Unit)
    }

    override suspend fun markSynced(id: String, syncedAt: Long): Result<Unit> = Result.Success(Unit)
    override suspend fun setSyncState(id: String, state: SyncState): Result<Unit> = Result.Success(Unit)
}

/** Minimal PreferencesRepository fake with a fast auto-save delay. */
private class FakePreferencesRepository(private val autoSaveDelayMs: Long = 50) : PreferencesRepository {
    override val settings: Flow<AppSettings> = MutableStateFlow(
        AppSettings(autoSaveDelayMs = autoSaveDelayMs)
    )

    override suspend fun setThemeMode(mode: ThemeMode) = Unit
    override suspend fun setDefaultEditorMode(mode: EditorMode) = Unit
    override suspend fun setAutoSave(enabled: Boolean) = Unit
    override suspend fun setAutoSaveDelayMs(delayMs: Long) = Unit
    override suspend fun setWordGoal(goal: Int) = Unit
    override suspend fun setAiModel(model: String) = Unit
    override suspend fun setBackendUrl(url: String) = Unit
    override suspend fun setSyncEnabled(enabled: Boolean) = Unit
    override suspend fun setDefaultExportFormat(format: ExportFormat) = Unit
    override suspend fun recordAiUsage() = Unit
}
