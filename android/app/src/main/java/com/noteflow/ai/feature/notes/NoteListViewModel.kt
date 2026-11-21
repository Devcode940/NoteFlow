package com.noteflow.ai.feature.notes

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.noteflow.ai.core.common.Result
import com.noteflow.ai.domain.model.Folder
import com.noteflow.ai.domain.model.Note
import com.noteflow.ai.domain.model.NoteFilter
import com.noteflow.ai.domain.model.NoteFilterType
import com.noteflow.ai.domain.model.SortBy
import com.noteflow.ai.domain.model.SortOrder
import com.noteflow.ai.domain.model.SyncState
import com.noteflow.ai.domain.repository.FolderRepository
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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class NoteListUiState(
    val notes: List<Note> = emptyList(),
    val folders: List<Folder> = emptyList(),
    val syncStates: Map<String, SyncState> = emptyMap(),
    val query: String = "",
    val sortBy: SortBy = SortBy.UPDATED_AT,
    val sortOrder: SortOrder = SortOrder.DESC,
    val isLoading: Boolean = true,
    val error: String? = null,
    val selectionMode: Boolean = false,
    val selectedIds: Set<String> = emptySet(),
    val message: String? = null
)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class NoteListViewModel @Inject constructor(
    private val noteRepository: NoteRepository,
    private val folderRepository: FolderRepository,
    private val createNote: CreateNoteUseCase,
    private val trashNote: TrashNoteUseCase,
    private val restoreNote: RestoreNoteUseCase,
    private val deletePermanently: DeleteNotePermanentlyUseCase,
    private val duplicateNote: DuplicateNoteUseCase,
    private val togglePin: TogglePinUseCase,
    private val toggleFavorite: ToggleFavoriteUseCase,
    private val toggleArchive: ToggleArchiveUseCase,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val filterType: NoteFilterType =
        runCatching { NoteFilterType.valueOf(savedStateHandle.get<String>("filter") ?: "ALL") }
            .getOrDefault(NoteFilterType.ALL)
    private val filterArg: String = savedStateHandle.get<String>("arg").orEmpty()

    private val query = MutableStateFlow("")
    private val sortBy = MutableStateFlow(SortBy.UPDATED_AT)
    private val sortOrder = MutableStateFlow(SortOrder.DESC)
    private val selectionMode = MutableStateFlow(false)
    private val selectedIds = MutableStateFlow<Set<String>>(emptySet())
    private val message = MutableStateFlow<String?>(null)

    private val filter = NoteFilter(
        type = filterType,
        folderId = if (filterType == NoteFilterType.FOLDER) filterArg.ifBlank { null } else null,
        tag = if (filterType == NoteFilterType.TAG) filterArg.ifBlank { null } else null
    )

    val uiState: StateFlow<NoteListUiState> = combine(
        noteRepository.observeNotes(filter, SortBy.UPDATED_AT, SortOrder.DESC, ""),
        folderRepository.observeFolders(),
        noteRepository.observeSyncStates(),
        query,
        sortBy,
        sortOrder,
        selectionMode,
        selectedIds,
        message
    ) { args ->
        @Suppress("UNCHECKED_CAST")
        NoteListUiState(
            notes = args[0] as List<Note>,
            folders = args[1] as List<Folder>,
            syncStates = args[2] as Map<String, SyncState>,
            query = args[3] as String,
            sortBy = args[4] as SortBy,
            sortOrder = args[5] as SortOrder,
            isLoading = false,
            selectionMode = args[6] as Boolean,
            selectedIds = args[7] as Set<String>,
            message = args[8] as String?
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), NoteListUiState())

    /** Filtered + sorted view of the loaded notes for the current query/sort. */
    val visibleNotes: StateFlow<List<Note>> = combine(uiState, query) { state, q ->
        val filtered = if (q.isBlank()) {
            state.notes
        } else {
            val needle = q.trim().lowercase()
            state.notes.filter {
                it.title.lowercase().contains(needle) || it.content.lowercase().contains(needle)
            }
        }
        when (state.sortBy) {
            SortBy.UPDATED_AT -> if (state.sortOrder == SortOrder.DESC) filtered.sortedByDescending { it.updatedAt } else filtered.sortedBy { it.updatedAt }
            SortBy.CREATED_AT -> if (state.sortOrder == SortOrder.DESC) filtered.sortedByDescending { it.createdAt } else filtered.sortedBy { it.createdAt }
            SortBy.TITLE -> if (state.sortOrder == SortOrder.DESC) filtered.sortedByDescending { it.title.lowercase() } else filtered.sortedBy { it.title.lowercase() }
            SortBy.CUSTOM -> filtered.sortedBy { it.sortOrder }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun onQueryChange(value: String) {
        query.value = value
    }

    fun onSortChange(by: SortBy, order: SortOrder) {
        sortBy.value = by
        sortOrder.value = order
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

    /** Moves a note to trash; the UI shows an Undo action that calls [restore]. */
    fun trash(note: Note) {
        viewModelScope.launch {
            when (val result = trashNote(note.id)) {
                is Result.Success -> message.value = "Note moved to trash"
                is Result.Failure -> message.value = result.message
            }
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
                is Result.Success -> message.value = "Note deleted permanently"
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
}
