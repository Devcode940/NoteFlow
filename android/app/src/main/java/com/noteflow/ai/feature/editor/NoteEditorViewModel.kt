package com.noteflow.ai.feature.editor

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.noteflow.ai.core.common.Result
import com.noteflow.ai.core.common.TextStats
import com.noteflow.ai.domain.model.Note
import com.noteflow.ai.domain.repository.NoteRepository
import com.noteflow.ai.domain.usecase.SaveNoteUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@HiltViewModel
class NoteEditorViewModel @Inject constructor(
    private val noteRepository: NoteRepository,
    private val saveNote: SaveNoteUseCase,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val noteId: String? = savedStateHandle.get<String>("noteId")

    private val isDirty = MutableStateFlow(false)
    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    val uiState: StateFlow<NoteEditorUiState> = combine(
        noteRepository.observeNote(noteId)
    ) { note ->
        NoteEditorUiState(
            note = note,
            isDirty = isDirty.value,
            message = _message.value
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), NoteEditorUiState())

    val stats: StateFlow<TextStats> by combine(uiState) { state ->
        TextStats.forContent(state.note?.content ?: "")
    }

    val title: StateFlow<String> by combine(uiState) { state ->
        state.note?.title ?: ""
    }

    val content: StateFlow<String> by combine(uiState) { state ->
        state.note?.content ?: ""
    }

    val isReadOnly by combine(uiState) { state ->
        state.note == null || state.note.trashed
    }

    fun onTitleChange(title: String) {
        val note = uiState.value.note ?: return
        if (title != note.title) {
            isDirty.value = true
        }
    }

    fun onContentChange(content: String) {
        val note = uiState.value.note ?: return
        if (content != note.content) {
            isDirty.value = true
        }
    }

    fun insertVoiceText(text: String) {
        isDirty.value = true
        // Consumed by the editor composable via onInsert callback.
    }

    fun setMessage(message: String?) {
        _message.value = message
    }

    fun save() {
        val state = uiState.value
        val note = state.note ?: return
        viewModelScope.launch {
            when (val result = saveNote(id = note.id, title = note.title, content = note.content)) {
                is Result.Success -> {
                    isDirty.value = false
                    _message.value = "Saved"
                }
                is Result.Failure -> _message.value = result.message
            }
        }
    }

    fun consumeMessage() {
        _message.value = null
    }
}

data class NoteEditorUiState(
    val note: Note? = null,
    val isDirty: Boolean = false,
    val message: String? = null
)
