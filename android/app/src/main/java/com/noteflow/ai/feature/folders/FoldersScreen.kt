package com.noteflow.ai.feature.folders

import androidx.compose.foundation.ExperimentalFoundationApi
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
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
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

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
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
        topBar = { androidx.compose.material3.TopAppBar(title = { Text("Folders") }) },
        floatingActionButton = {
            androidx.compose.material3.ExtendedFloatingActionButton(
                onClick = { showAdd = true },
                icon = { Icon(Icons.Default.Add, contentDescription = null) },
                text = { Text("New folder") }
            )
        }
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
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

    if (showAdd) {
        FolderDialog(
            visible = true,
            onDismiss = { showAdd = false },
            name = newName,
            onNameChange = { newName = it },
            icon = newIcon,
            onIconChange = { newIcon = it },
            color = newColor,
            onColorChange = { newColor = it },
            onSave = {
                if (newName.isBlank()) return
                viewModel.createFolder(newName, newIcon, newColor)
                showAdd = false
                newName = ""
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
            .clickable(onClick = onClick)
            .padding(bottom = 4.dp),
        colors = androidx.compose.material3.CardDefaults.cardColors(
            containerColor = Color(
                0x0F000000 or (parseFolderColor(folder.colorHex).value.toInt() and 0x00FFFFFF)
            )
        )
    ) {
        Row(modifier = Modifier.padding(16.dp)) {
            Text(
                text = folder.icon,
                style = MaterialTheme.typography.displaySmall,
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
    visible: Boolean,
    onDismiss: () -> Unit,
    name: String,
    onNameChange: (String) -> Unit,
    icon: String,
    onIconChange: (String) -> Unit,
    color: String,
    onColorChange: (String) -> Unit,
    onSave: () -> Unit
) {
    if (!visible) return
    androidx.compose.material3.Dialog(onDismissRequest = onDismiss) {
        Column(modifier = Modifier.padding(24.dp)) {
            Text("New folder", style = MaterialTheme.typography.headlineSmall)
            Spacer(modifier = Modifier.height(16.dp))
            androidx.compose.material3.OutlinedTextField(
                value = name,
                onValueChange = onNameChange,
                label = { Text("Name") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text("Icon", style = MaterialTheme.typography.labelMedium)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                FolderIconPalette.forEach { emoji ->
                    androidx.compose.material3.FilterChip(
                        selected = icon == emoji,
                        onClick = { onIconChange(emoji) },
                        label = { Text(emoji) }
                    )
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
            Text("Color", style = MaterialTheme.typography.labelMedium)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                FolderColorPalette.forEach { hex ->
                    androidx.compose.material3.FilterChip(
                        selected = color == hex,
                        onClick = { onColorChange(hex) },
                        label = { Text("") },
                        colors = androidx.compose.material3.ChipDefaults.colors(
                            containerColor = Color(
                                0xFF000000 or (hex.toLongOrNull(16)?.let { it.toInt() } ?: 0xFF6D28D9)
                            )
                        )
                    )
                }
            }
            Spacer(modifier = Modifier.height(20.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                androidx.compose.material3.TextButton(onClick = onDismiss) { Text("Cancel") }
                androidx.compose.material3.Button(onClick = onSave) { Text("Save") }
            }
        }
    }
}
