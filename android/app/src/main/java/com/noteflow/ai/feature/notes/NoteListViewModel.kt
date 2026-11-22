package com.noteflow.ai.feature.notes

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.noteflow.ai.core.common.Result
import com.noteflow.ai.domain.model.Note
import com.noteflow.ai.domain.model.NoteFilter
import com.noteflow.ai.domain.model.NoteFilterType
import com.noteflow.ai.domain.model.SortBy
import com.noteflow.ai.domain.model.SortOrder
import com.noteflow.ai.domain.model.SyncState
import com.noteflow.ai.domain.repository.NoteRepository
import com.noteflow.ai.domain.usecase.CreateNoteUseCase
import com.noteflow.ai.domain.usecase.DeleteNotePermanentlyUseCase
import com.noteflow.ai.domain.usecase.DuplicateNoteUseCase
import com.noteflow.ai.domain.usecase.RestoreNoteUseCase
import com.noteflow.ai.domain.usecase.ToggleArchiveUseCase
import com.noteflow.ai.domain.usecase.ToggleFavoriteUseCase
import com.noteflow.ai.domain.usecase.TogglePinUseCase
import com.noteflow.ai.domain.usecase.TrashNoteUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class NoteListUiState(
    val syncStates: Map<String, SyncState> = emptyMap(),
    val query: String = "",
    val sortBy: SortBy = SortBy.UPDATED_AT,
    val sortOrder: SortOrder = SortOrder.DESC,
    val isLoading: Boolean = true,
    val canUndo: Boolean = false,
    val selectionMode: Boolean = false,
    val selectedIds: Set<String> = emptySet(),
    val message: String? = null
)

@OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
@HiltViewModel
class NoteListViewModel @Inject constructor(
    private val noteRepository: NoteRepository,
    private val createNote: CreateNoteUseCase,
    private val trashNote: TrashNoteUseCase,
    private val restoreNote: RestoreNoteUseCase,
    private val deletePermanently: DeleteNotePermanentlyUseCase,
    private val duplicateNote: DuplicateNoteUseCase,
    private val togglePin: TogglePinUseCase,
    private val toggleFavorite: ToggleFavoriteUseCase,
    private val toggleArchive: ToggleArchiveUseCase,
    private val savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val filterType: NoteFilterType =
        runCatching { NoteFilterType.valueOf(savedStateHandle.get<String>("filter") ?: "ALL") }
            .getOrDefault(NoteFilterType.ALL)
    private val filterArg: String = savedStateHandle.get<String>("arg").orEmpty()

    // Transient list state survives process death via SavedStateHandle (P1-7 fix).
    private val query = MutableStateFlow(savedStateHandle.get<String>(KEY_QUERY) ?: "")
    private val sortBy = MutableStateFlow(
        savedStateHandle.get<String>(KEY_SORT_BY)?.let { name ->
            runCatching { SortBy.valueOf(name) }.getOrNull()
        } ?: SortBy.UPDATED_AT
    )
    private val sortOrder = MutableStateFlow(
        savedStateHandle.get<String>(KEY_SORT_ORDER)?.let { name ->
            runCatching { SortOrder.valueOf(name) }.getOrNull()
        } ?: SortOrder.DESC
    )
    private val selectionMode = MutableStateFlow(false)
    private val selectedIds = MutableStateFlow<Set<String>>(emptySet())
    private val message = MutableStateFlow<String?>(null)

    /** Notes trashed since the last Undo, enabling batch restore (P1-11 fix). */
    private val pendingUndo = MutableStateFlow<List<Note>>(emptyList())

    private val filter = NoteFilter(
        type = filterType,
        folderId = if (filterType == NoteFilterType.FOLDER) filterArg.ifBlank { null } else null,
        tag = if (filterType == NoteFilterType.TAG) filterArg.ifBlank { null } else null
    )

    /**
     * Single source of truth for the visible list: query/sort live in this
     * ViewModel, but filtering, searching and sorting all happen in the
     * repository/Room (P2-5 fix — no in-memory re-sorting duplicate).
     */
    val visibleNotes: StateFlow<List<com.noteflow.ai.domain.model.Note>> =
        combine(query.debounce(150), sortBy, sortOrder) { q, by, order -> Triple(q, by, order) }
            .flatMapLatest { (q, by, order) ->
                noteRepository.observeNotes(filter, by, order, q)
            }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val uiState: StateFlow<NoteListUiState> = combine(
        noteRepository.observeSyncStates(),
        query,
        sortBy,
        sortOrder,
        selectionMode,
        selectedIds,
        message,
        pendingUndo.map { it.isNotEmpty() }
    ) { args ->
        @Suppress("UNCHECKED_CAST")
        NoteListUiState(
            syncStates = args[0] as Map<String, SyncState>,
            query = args[1] as String,
            sortBy = args[2] as SortBy,
            sortOrder = args[3] as SortOrder,
            isLoading = false,
            canUndo = args[7] as Boolean,
            selectionMode = args[4] as Boolean,
            selectedIds = args[5] as Set<String>,
            message = args[6] as String?
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), NoteListUiState())

    fun onQueryChange(value: String) {
        query.value = value
        savedStateHandle[KEY_QUERY] = value
    }

    fun onSortChange(by: SortBy, order: SortOrder) {
        sortBy.value = by
        sortOrder.value = order
        savedStateHandle[KEY_SORT_BY] = by.name
        savedStateHandle[KEY_SORT_ORDER] = order.name
    }

    fun createNewNote(onCreated: (String) -> Unit) {
        viewModelScope.launch {
            when (val result = createNote(folderId = filter.folderId)) {
                is Result.Success -> onCreated(result.data.id)
                is Result.Failure -> message.value = result.message
            }
        }
    }

    fun togglePin(note: Note) {
        viewModelScope.launch { togglePin(note.id, !note.pinned) }
    }

    fun toggleFavorite(note: Note) {
        viewModelScope.launch { toggleFavorite(note.id, !note.favorite) }
    }

    fun toggleArchive(note: Note) {
        viewModelScope.launch {
            toggleArchive(note.id, !note.archived)
            message.value = if (note.archived) "Note restored from archive" else "Note archived"
        }
    }

    fun duplicate(note: Note) {
        viewModelScope.launch {
            when (val result = duplicateNote(note.id)) {
                is Result.Success -> message.value = "Note duplicated"
                is Result.Failure -> message.value = result.message
            }
        }
    }

    /** Moves one note to trash and arms Undo. */
    fun trash(note: Note) {
        trashAll(listOf(note))
    }

    /** Moves many notes to trash with a single combined Undo action (P1-11 fix). */
    fun trashAll(notes: List<Note>) {
        if (notes.isEmpty()) return
        viewModelScope.launch {
            var failed = 0
            for (note in notes) {
                when (trashNote(note.id)) {
                    is Result.Success -> Unit
                    is Result.Failure -> failed++
                }
            }
            if (failed == notes.size) {
                message.value = "Could not move notes to trash"
            } else {
                pendingUndo.value = pendingUndo.value + notes
                message.value = if (notes.size == 1) {
                    "Note moved to trash"
                } else {
                    "${notes.size - failed} notes moved to trash"
                }
            }
        }
    }

    /** Restores every note from the last batch trash. */
    fun undoTrash() {
        val toRestore = pendingUndo.value
        if (toRestore.isEmpty()) return
        viewModelScope.launch {
            var restored = 0
            for (note in toRestore) {
                if (restoreNote(note.id) is Result.Success) restored++
            }
            pendingUndo.value = emptyList()
            message.value = "$restored note${if (restored == 1) "" else "s"} restored"
        }
    }

    fun restore(note: Note) {
        viewModelScope.launch {
            when (val result = restoreNote(note.id)) {
                is Result.Success -> message.value = "Note restored"
                is Result.Failure -> message.value = result.message
            }
        }
    }

    fun deletePermanently(note: Note) {
        viewModelScope.launch {
            when (val result = deletePermanently(note.id)) {
                is Result.Success -> {
                    pendingUndo.value = pendingUndo.value.filterNot { it.id == note.id }
                    message.value = "Note deleted permanently"
                }
                is Result.Failure -> message.value = result.message
            }
        }
    }

    fun toggleSelection(id: String) {
        val current = selectedIds.value.toMutableSet()
        if (!current.add(id)) current.remove(id)
        selectedIds.value = current
        selectionMode.value = current.isNotEmpty()
    }

    fun clearSelection() {
        selectedIds.value = emptySet()
        selectionMode.value = false
    }

    fun consumeMessage() {
        message.value = null
    }

    companion object {
        private const val KEY_QUERY = "noteflow.query"
        private const val KEY_SORT_BY = "noteflow.sortBy"
        private const val KEY_SORT_ORDER = "noteflow.sortOrder"
    }
}
