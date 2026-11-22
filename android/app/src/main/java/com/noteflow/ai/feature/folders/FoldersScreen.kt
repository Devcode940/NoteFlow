package com.noteflow.ai.feature.folders

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.noteflow.ai.core.designsystem.FolderColorPalette
import com.noteflow.ai.core.designsystem.FolderIconPalette
import com.noteflow.ai.core.designsystem.parseFolderColor
import com.noteflow.ai.domain.model.Folder

/** Folder management. The host NavHost provides the screen title bar. */
@Composable
fun FoldersScreen(
    onOpenFolder: (String) -> Unit,
    viewModel: FoldersViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsState()
    var showAdd by remember { mutableStateOf(false) }
    var newName by remember { mutableStateOf("") }
    var newIcon by remember { mutableStateOf(FolderIconPalette.first()) }
    var newColor by remember { mutableStateOf(FolderColorPalette.first()) }

    Scaffold(
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { showAdd = true },
                icon = { Icon(Icons.Default.Add, contentDescription = null) },
                text = { Text("New folder") }
            )
        }
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            if (state.folders.isEmpty()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center
                ) {
                    Text(
                        "No folders yet — create one to organize your notes.",
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
                    items(state.folders, key = { it.id }) { folder ->
                        FolderRow(
                            folder = folder,
                            onClick = { onOpenFolder(folder.id) },
                            onDelete = { viewModel.deleteFolder(folder.id) }
                        )
                    }
                }
            }
        }
    }

    if (showAdd) {
        FolderDialog(
            name = newName,
            onNameChange = { newName = it },
            icon = newIcon,
            onIconChange = { newIcon = it },
            color = newColor,
            onColorChange = { newColor = it },
            onDismiss = { showAdd = false },
            onSave = {
                if (newName.isNotBlank()) {
                    viewModel.createFolder(newName, newIcon, newColor)
                    newName = ""
                    showAdd = false
                }
            }
        )
    }
}

@Composable
private fun FolderRow(
    folder: Folder,
    onClick: () -> Unit,
    onDelete: () -> Unit
) {
    var showMenu by remember { mutableStateOf(false) }
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(
            containerColor = parseFolderColor(folder.colorHex).copy(alpha = 0.08f)
        )
    ) {
        Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            // Emoji glyph is decorative: the folder name is announced to screen readers (a11y fix).
            Text(
                text = folder.icon,
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.width(32.dp)
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = folder.name,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Box {
                IconButton(onClick = { showMenu = true }) {
                    Icon(Icons.Default.MoreVert, contentDescription = "Folder actions")
                }
                DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                    DropdownMenuItem(text = { Text("Delete") }, onClick = { showMenu = false; onDelete() })
                }
            }
        }
    }
}

@Composable
private fun FolderDialog(
    name: String,
    onNameChange: (String) -> Unit,
    icon: String,
    onIconChange: (String) -> Unit,
    color: String,
    onColorChange: (String) -> Unit,
    onDismiss: () -> Unit,
    onSave: () -> Unit
) {
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("New folder") },
        text = {
            Column {
                OutlinedTextField(
                    value = name,
                    onValueChange = onNameChange,
                    label = { Text("Name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(12.dp))
                Text("Icon", style = MaterialTheme.typography.labelMedium)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    FolderIconPalette.forEach { emoji ->
                        FilterChip(
                            selected = icon == emoji,
                            onClick = { onIconChange(emoji) },
                            label = { Text(emoji) }
                        )
                    }
                }
                Spacer(modifier = Modifier.height(12.dp))
                Text("Color", style = MaterialTheme.typography.labelMedium)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    FolderColorPalette.forEach { hex ->
                        FilterChip(
                            selected = color == hex,
                            onClick = { onColorChange(hex) },
                            label = { Text(" ") },
                            colors = FilterChipDefaults.filterChipColors(
                                containerColor = parseFolderColor(hex)
                            )
                        )
                    }
                }
            }
        },
        confirmButton = { Button(onClick = onSave) { Text("Create") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}
