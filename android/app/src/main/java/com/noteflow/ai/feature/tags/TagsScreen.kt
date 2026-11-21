package com.noteflow.ai.feature.tags

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Tag
import androidx.compose.material3.Card
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.noteflow.ai.domain.repository.NoteRepository
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@HiltViewModel
class TagsViewModel @Inject constructor(
    private val noteRepository: NoteRepository
) : ViewModel() {
    val uiState: StateFlow<TagsUiState> = noteRepository.observeAllTags()
        .map { TagsUiState(tags = it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), TagsUiState())
}

data class TagsUiState(val tags: List<String> = emptyList())

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun TagsScreen(
    onOpenTag: (String) -> Unit,
    viewModel: TagsViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsState()
    Scaffold(
        topBar = { androidx.compose.material3.TopAppBar(title = { Text("Tags") }) },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { /* tags are created implicitly when tagging a note */ },
                icon = { Icon(Icons.Default.Tag, contentDescription = null) },
                text = { Text("Tags") }
            )
        }
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            if (state.tags.isEmpty()) {
                androidx.compose.foundation.layout.Row(
                    modifier = androidx.compose.foundation.layout.RowScope.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center
                ) {
                    Text("No tags yet", style = MaterialTheme.typography.bodyMedium)
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(state.tags, key = { it }) { tag ->
                        Card(
                            modifier = Modifier.fillMaxWidth().clickable(onClick = { onOpenTag(tag) })
                        ) {
                            androidx.compose.foundation.layout.Row(
                                modifier = Modifier.padding(16.dp),
                                verticalAlignment = androidx.compose.foundation.layout.Alignment.CenterVertically
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
}
