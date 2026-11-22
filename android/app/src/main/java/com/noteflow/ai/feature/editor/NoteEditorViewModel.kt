package com.noteflow.ai.feature.editor

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.noteflow.ai.core.common.Logger
import com.noteflow.ai.core.common.Result
import com.noteflow.ai.core.common.TextStats
import com.noteflow.ai.domain.model.Note
import com.noteflow.ai.domain.repository.NoteRepository
import com.noteflow.ai.domain.repository.PreferencesRepository
import com.noteflow.ai.domain.usecase.CreateNoteUseCase
import com.noteflow.ai.domain.usecase.SaveNoteContentUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Editor ViewModel that OWNS the edited title and content (P1-1 fix).
 *
 * Load: the repository note is read once and seeds the editing state.
 * Edit: onTitleChange/onContentChange store the new values and mark dirty —
 *       the text is never discarded.
 * Save: debounced auto-save (settings.autoSaveDelayMs) plus an explicit
 *       save() write the editing state through SaveNoteContentUseCase, so
 *       every save persists exactly what the user typed.
 *
 * The "new" route creates a real note first and emits [createdNoteId] so the
 * NavHost can replace the route (P1-3 fix).
 */
@OptIn(FlowPreview::class)
@HiltViewModel
class NoteEditorViewModel @Inject constructor(
    private val noteRepository: NoteRepository,
    private val saveNoteContent: SaveNoteContentUseCase,
    private val createNoteUseCase: CreateNoteUseCase,
    private val preferencesRepository: PreferencesRepository,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val requestedId: String = savedStateHandle.get<String>("noteId") ?: "new"

    private val _note = MutableStateFlow<Note?>(null)
    val note: StateFlow<Note?> = _note.asStateFlow()

    /** True when the user has edits not yet persisted. */
    private val _isDirty = MutableStateFlow(false)
    val isDirty: StateFlow<Boolean> = _isDirty.asStateFlow()

    /** Editing state — the source of truth for what the user typed. */
    private val _title = MutableStateFlow("")
    val title: StateFlow<String> = _title.asStateFlow()

    private val _content = MutableStateFlow("")
    val content: StateFlow<String> = _content.asStateFlow()

    /** Live word/char statistics derived from the content being edited. */
    val stats: StateFlow<TextStats.ContentStats> = _content
        .map { TextStats.forContent(it) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, TextStats.forContent(""))

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    /** Set when the "new" route has created the backing note; the screen navigates. */
    private val _createdNoteId = MutableStateFlow<String?>(null)
    val createdNoteId: StateFlow<String?> = _createdNoteId.asStateFlow()

    val isReadOnly: Boolean
        get() = _note.value == null || _note.value?.trashed == true

    private var autoSaveJob: Job? = null
    private var creationRequested = false

    init {
        if (requestedId == "new" || requestedId.isBlank()) {
            createBackingNote()
        } else {
            loadNote(requestedId)
        }
    }

    private fun loadNote(id: String) {
        viewModelScope.launch {
            try {
                val loaded = noteRepository.observeNote(id).first()
                if (loaded == null) {
                    // Unknown id (e.g. stale route after process restore):
                    // create a real note so the editor is never dead.
                    createBackingNote()
                    return@launch
                }
                _note.value = loaded
                _title.value = loaded.title
                _content.value = loaded.content
                _isDirty.value = false
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Logger.e(TAG, "Loading note failed", e)
                _message.value = "Could not open the note"
            }
        }
    }

    private fun createBackingNote() {
        if (creationRequested) return
        creationRequested = true
        viewModelScope.launch {
            try {
                when (val result = createNoteUseCase()) {
                    is Result.Success -> {
                        val created = result.data
                        _note.value = created
                        _title.value = created.title
                        _content.value = created.content
                        _isDirty.value = false
                        _createdNoteId.value = created.id
                    }
                    is Result.Failure -> _message.value = result.message
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Logger.e(TAG, "Creating note failed", e)
                _message.value = "Could not create the note"
            }
        }
    }

    fun onTitleChange(value: String) {
        if (isReadOnly) return
        _title.value = value
        if (value != _note.value?.title) _isDirty.value = true
        scheduleAutoSave()
    }

    fun onContentChange(value: String) {
        if (isReadOnly) return
        _content.value = value
        if (value != _note.value?.content) _isDirty.value = true
        scheduleAutoSave()
    }

    fun insertVoiceText(text: String) {
        val current = _content.value
        val insertion = if (current.isBlank() || current.endsWith(' ') || current.endsWith('\n')) text else " $text"
        onContentChange(current + insertion)
    }

    /** Cancels any pending auto-save timer. */
    fun cancelPendingSave() {
        autoSaveJob?.cancel()
        autoSaveJob = null
    }

    fun save() {
        val note = _note.value ?: return
        if (isReadOnly) return
        cancelPendingSave()
        viewModelScope.launch {
            persist(note.id, _title.value, _content.value)
        }
    }

    private fun scheduleAutoSave() {
        if (!_isDirty.value) return
        autoSaveJob?.cancel()
        autoSaveJob = viewModelScope.launch {
            val delayMs = preferencesRepository.settings.first().autoSaveDelayMs.coerceAtLeast(200)
            delay(delayMs)
            val note = _note.value ?: return@launch
            persist(note.id, _title.value, _content.value)
        }
    }

    private suspend fun persist(id: String, title: String, content: String) {
        try {
            when (val result = saveNoteContent(id, title, content)) {
                is Result.Success -> {
                    _isDirty.value = false
                    _note.value = _note.value?.copy(title = title, content = content)
                }
                is Result.Failure -> _message.value = result.message
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Logger.e(TAG, "Saving note failed", e)
            _message.value = "Could not save the note"
        }
    }

    fun setMessage(message: String?) {
        _message.value = message
    }

    fun consumeMessage() {
        _message.value = null
    }

    override fun onCleared() {
        autoSaveJob?.cancel()
        super.onCleared()
    }

    companion object {
        private const val TAG = "NoteEditorVM"
    }
}
