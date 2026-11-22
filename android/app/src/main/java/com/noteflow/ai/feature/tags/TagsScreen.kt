package com.noteflow.ai.feature.tags

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Tag
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.noteflow.ai.domain.repository.NoteRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

@HiltViewModel
class TagsViewModel @Inject constructor(
    noteRepository: NoteRepository
) : ViewModel() {
    val uiState: StateFlow<TagsUiState> = noteRepository.observeAllTags()
        .map { TagsUiState(tags = it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), TagsUiState())
}

data class TagsUiState(val tags: List<String> = emptyList())

/** Tag cloud; tags are created implicitly by tagging notes. Host provides the title bar. */
@Composable
fun TagsScreen(
    onOpenTag: (String) -> Unit,
    viewModel: TagsViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsState()
    Column(modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        if (state.tags.isEmpty()) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center
            ) {
                Text(
                    "No tags yet — add tags to a note and they appear here.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 24.dp)
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(state.tags, key = { it }) { tag ->
                    Card(modifier = Modifier.fillMaxWidth().clickable(onClick = { onOpenTag(tag) })) {
                        Row(
                            modifier = Modifier.padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.Tag, contentDescription = null)
                            Spacer(modifier = Modifier.width(12.dp))
                            Text("#$tag", style = MaterialTheme.typography.titleMedium)
                        }
                    }
                }
            }
        }
    }
}
