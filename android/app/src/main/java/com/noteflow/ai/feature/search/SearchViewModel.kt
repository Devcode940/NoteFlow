package com.noteflow.ai.feature.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.noteflow.ai.core.common.Result
import com.noteflow.ai.domain.model.Note
import com.noteflow.ai.domain.repository.NoteRepository
import com.noteflow.ai.domain.usecase.SearchNotesUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@HiltViewModel
class SearchViewModel @Inject constructor(
    private val noteRepository: NoteRepository,
    private val searchNotes: SearchNotesUseCase
) : ViewModel() {

    private val query = MutableStateFlow("")
    private val results = MutableStateFlow<List<Note>>(emptyList())
    private val message = MutableStateFlow<String?>(null)

    val uiState: StateFlow<SearchUiState> = combine(
        query,
        results,
        message
    ) { q, res, msg ->
        SearchUiState(query = q, results = res, message = msg)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SearchUiState())

    fun onQueryChange(value: String) {
        query.value = value
        if (value.isBlank()) {
            results.value = emptyList()
        } else {
            search(value)
        }
    }

    private fun search(value: String) {
        viewModelScope.launch {
            when (val result = searchNotes(value)) {
                is Result.Success -> results.value = result.data
                is Result.Failure -> message.value = result.message
            }
        }
    }

    fun consumeMessage() {
        message.value = null
    }
}

data class SearchUiState(
    val query: String = "",
    val results: List<Note> = emptyList(),
    val message: String? = null
)
