package com.noteflow.ai.feature.notes

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.Sort
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.ctrlPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.noteflow.ai.core.common.TextStats
import com.noteflow.ai.domain.model.Note
import com.noteflow.ai.domain.model.NoteFilterType
import com.noteflow.ai.domain.model.SortBy
import com.noteflow.ai.domain.model.SortOrder
import com.noteflow.ai.domain.model.SyncState
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun NoteListScreen(
    filterType: NoteFilterType,
    folderId: String?,
    tag: String?,
    onOpenNote: (String) -> Unit,
    onOpenFolder: (String) -> Unit,
    onOpenTag: (String) -> Unit,
    viewModel: NoteListViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsState()
    val notes by viewModel.visibleNotes.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val searchFocus = remember { FocusRequester() }
    var lastTrashed by remember { mutableStateOf<Note?>(null) }
    var showSortMenu by remember { mutableStateOf(false) }

    LaunchedEffect(state.message) {
        val message = state.message ?: return@LaunchedEffect
        val result = snackbarHostState.showSnackbar(
            message = message,
            actionLabel = if (message == "Note moved to trash") "Undo" else null
        )
        if (result == SnackbarResult.ActionPerformed) {
            lastTrashed?.let { viewModel.restore(it) }
        }
        viewModel.consumeMessage()
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        floatingActionButton = {
            if (!state.selectionMode) {
                ExtendedFloatingActionButton(
                    onClick = { viewModel.createNewNote(onOpenNote) },
                    icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                    text = { Text("New note") }
                )
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .onPreviewKeyEvent { event ->
                    when {
                        event.ctrlPressed && event.key == Key.N -> {
                            viewModel.createNewNote(onOpenNote)
                            true
                        }
                        event.ctrlPressed && event.key == Key.F -> {
                            searchFocus.requestFocus()
                            true
                        }
                        else -> false
                    }
                }
                .padding(horizontal = 16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = state.query,
                    onValueChange = viewModel::onQueryChange,
                    placeholder = { Text("Search notes") },
                    leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                    singleLine = true,
                    modifier = Modifier
                        .weight(1f)
                        .focusRequester(searchFocus)
                )
                Box {
                    IconButton(onClick = { showSortMenu = true }) {
                        Icon(Icons.Filled.Sort, contentDescription = "Sort notes")
                    }
                    DropdownMenu(expanded = showSortMenu, onDismissRequest = { showSortMenu = false }) {
                        SortOption("Recently updated", SortBy.UPDATED_AT, SortOrder.DESC) {
                            viewModel.onSortChange(SortBy.UPDATED_AT, SortOrder.DESC)
                        }
                        SortOption("Recently created", SortBy.CREATED_AT, SortOrder.DESC) {
                            viewModel.onSortChange(SortBy.CREATED_AT, SortOrder.DESC)
                        }
                        SortOption("Title A–Z", SortBy.TITLE, SortOrder.ASC) {
                            viewModel.onSortChange(SortBy.TITLE, SortOrder.ASC)
                        }
                        SortOption("Title Z–A", SortBy.TITLE, SortOrder.DESC) {
                            viewModel.onSortChange(SortBy.TITLE, SortOrder.DESC)
                        }
                        SortOption("Custom order", SortBy.CUSTOM, SortOrder.ASC) {
                            viewModel.onSortChange(SortBy.CUSTOM, SortOrder.ASC)
                        }
                    }
                }
            }

            if (state.selectionMode) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("${state.selectedIds.size} selected", style = MaterialTheme.typography.titleMedium)
                    Row {
                        TextButton(onClick = {
                            state.selectedIds.forEach { id ->
                                notes.firstOrNull { it.id == id }?.let { viewModel.trash(it) }
                            }
                            viewModel.clearSelection()
                        }) {
                            Text("Move to trash")
                        }
                        TextButton(onClick = viewModel::clearSelection) { Text("Cancel") }
                    }
                }
            }

            when {
                state.isLoading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
                state.error != null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(state.error!!, color = MaterialTheme.colorScheme.error)
                        Spacer(Modifier.height(8.dp))
                        TextButton(onClick = { /* Room flows retry automatically */ }) {
                            Text("Dismiss")
                        }
                    }
                }
                notes.isEmpty() -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            emptyStateTitle(filterType, tag),
                            style = MaterialTheme.typography.titleMedium
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            emptyStateSubtitle(filterType),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                else -> LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(notes, key = { it.id }) { note ->
                        val dismissState = rememberSwipeToDismissBoxState(
                            confirmValueChange = { value ->
                                if (value == SwipeToDismissBoxValue.EndToStart) {
                                    lastTrashed = note
                                    viewModel.trash(note)
                                    true
                                } else {
                                    false
                                }
                            }
                        )
                        SwipeToDismissBox(
                            state = dismissState,
                            enableDismissFromStartToEnd = false,
                            backgroundContent = {
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .padding(horizontal = 16.dp),
                                    contentAlignment = Alignment.CenterEnd
                                ) {
                                    Icon(
                                        Icons.Filled.Delete,
                                        contentDescription = "Move to trash",
                                        tint = MaterialTheme.colorScheme.error
                                    )
                                }
                            }
                        ) {
                            NoteCard(
                                note = note,
                                syncState = state.syncStates[note.id],
                                selected = state.selectedIds.contains(note.id),
                                selectionMode = state.selectionMode,
                                onClick = {
                                    if (state.selectionMode) viewModel.toggleSelection(note.id)
                                    else onOpenNote(note.id)
                                },
                                onLongClick = { viewModel.toggleSelection(note.id) },
                                onTogglePin = { viewModel.togglePin(note) },
                                onToggleFavorite = { viewModel.toggleFavorite(note) },
                                onToggleArchive = { viewModel.toggleArchive(note) },
                                onDuplicate = { viewModel.duplicate(note) },
                                onTrash = {
                                    lastTrashed = note
                                    viewModel.trash(note)
                                },
                                onRestore = { viewModel.restore(note) },
                                onDeletePermanently = { viewModel.deletePermanently(note) },
                                onOpenTag = onOpenTag
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SortOption(label: String, by: SortBy, order: SortOrder, onClick: () -> Unit) {
    DropdownMenuItem(text = { Text(label) }, onClick = onClick)
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun NoteCard(
    note: Note,
    syncState: SyncState?,
    selected: Boolean,
    selectionMode: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onTogglePin: () -> Unit,
    onToggleFavorite: () -> Unit,
    onToggleArchive: () -> Unit,
    onDuplicate: () -> Unit,
    onTrash: () -> Unit,
    onRestore: () -> Unit,
    onDeletePermanently: () -> Unit,
    onOpenTag: (String) -> Unit
) {
    var showMenu by remember { mutableStateOf(false) }
    val dateFormat = remember { SimpleDateFormat("MMM d, yyyy", Locale.getDefault()) }
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(onClick = onClick, onLongClick = onLongClick),
        colors = CardDefaults.cardColors(
            containerColor = if (selected) {
                MaterialTheme.colorScheme.primaryContainer
            } else {
                MaterialTheme.colorScheme.surfaceContainerLow
            }
        )
    ) {
        Row(modifier = Modifier.padding(16.dp)) {
            if (selectionMode) {
                Icon(
                    if (selected) Icons.Filled.RadioButtonChecked else Icons.Filled.RadioButtonUnchecked,
                    contentDescription = null,
                    modifier = Modifier.padding(end = 8.dp)
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = note.title.ifBlank { "Untitled note" },
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    if (note.pinned) {
                        Icon(Icons.Filled.PushPin, contentDescription = "Pinned", modifier = Modifier.padding(start = 4.dp))
                    }
                    if (note.favorite) {
                        Icon(Icons.Filled.Star, contentDescription = "Favorite", modifier = Modifier.padding(start = 4.dp))
                    }
                    SyncIcon(syncState)
                    Box {
                        IconButton(onClick = { showMenu = true }) {
                            Icon(Icons.Filled.MoreVert, contentDescription = "Note actions")
                        }
                        DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                            DropdownMenuItem(
                                text = { Text(if (note.pinned) "Unpin" else "Pin") },
                                onClick = { showMenu = false; onTogglePin() }
                            )
                            DropdownMenuItem(
                                text = { Text(if (note.favorite) "Remove favorite" else "Add favorite") },
                                onClick = { showMenu = false; onToggleFavorite() }
                            )
                            DropdownMenuItem(
                                text = { Text(if (note.archived) "Restore from archive" else "Archive") },
                                onClick = { showMenu = false; onToggleArchive() }
                            )
                            DropdownMenuItem(
                                text = { Text("Duplicate") },
                                onClick = { showMenu = false; onDuplicate() }
                            )
                            if (note.trashed) {
                                DropdownMenuItem(
                                    text = { Text("Restore") },
                                    onClick = { showMenu = false; onRestore() }
                                )
                                DropdownMenuItem(
                                    text = { Text("Delete permanently") },
                                    onClick = { showMenu = false; onDeletePermanently() }
                                )
                            } else {
                                DropdownMenuItem(
                                    text = { Text("Move to trash") },
                                    onClick = { showMenu = false; onTrash() }
                                )
                            }
                        }
                    }
                }
                val preview = TextStats.extractPreview(note.content)
                if (preview.isNotEmpty()) {
                    Text(
                        text = preview,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Spacer(Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = dateFormat.format(Date(note.updatedAt)),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "  ·  ${note.wordCount} words",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    note.tags.take(3).forEach { tag ->
                        AssistChip(
                            onClick = { onOpenTag(tag) },
                            label = { Text("#$tag") },
                            modifier = Modifier.padding(start = 6.dp),
                            colors = AssistChipDefaults.assistChipColors()
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SyncIcon(state: SyncState?) {
    val (icon, description) = when (state) {
        SyncState.SYNCED -> Icons.Filled.CloudDone to "Synced"
        SyncState.PENDING -> Icons.Filled.CloudUpload to "Pending sync"
        SyncState.SYNCING -> Icons.Filled.CloudUpload to "Syncing"
        SyncState.FAILED -> Icons.Filled.CloudOff to "Sync failed"
        SyncState.LOCAL_ONLY, null -> Icons.Filled.CloudOff to "Local only"
    }
    Icon(
        icon,
        contentDescription = description,
        modifier = Modifier.padding(start = 4.dp),
        tint = MaterialTheme.colorScheme.onSurfaceVariant
    )
}

private fun emptyStateTitle(filter: NoteFilterType, tag: String?): String = when (filter) {
    NoteFilterType.ALL -> "No notes yet"
    NoteFilterType.RECENT -> "No recent notes"
    NoteFilterType.FAVORITES -> "No favorites"
    NoteFilterType.PINNED -> "No pinned notes"
    NoteFilterType.ARCHIVED -> "Archive is empty"
    NoteFilterType.TRASH -> "Trash is empty"
    NoteFilterType.FOLDER -> "No notes in this folder"
    NoteFilterType.TAG -> "No notes tagged ${tag?.let { "#$it" } ?: ""}"
}

private fun emptyStateSubtitle(filter: NoteFilterType): String = when (filter) {
    NoteFilterType.TRASH -> "Deleted notes appear here and can be restored."
    NoteFilterType.ARCHIVED -> "Archived notes stay here until you restore them."
    else -> "Tap New note to create your first note."
}
