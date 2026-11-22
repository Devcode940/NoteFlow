package com.noteflow.ai.feature.commandpalette

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.clickable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog

/**
 * Command palette triggered by Ctrl+K. Offers quick navigation and actions.
 */
@Composable
fun CommandPaletteDialog(
    onDismiss: () -> Unit,
    onCreateNote: () -> Unit,
    onSearch: () -> Unit,
    onCreateFolder: () -> Unit,
    onOpenAi: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenFavorites: () -> Unit,
    onOpenExports: () -> Unit,
    onSwitchTheme: () -> Unit = {},
    onArchiveNote: () -> Unit = {}
) {
    val commands = remember {
        listOf(
            CommandItem("create_note", "New note", "Create a blank note", Icons.Default.Add, onCreateNote),
            CommandItem("search", "Search notes", "Search across titles and content", Icons.Default.Search, onSearch),
            CommandItem("create_folder", "New folder", "Organize notes into folders", Icons.Default.Folder, onCreateFolder),
            CommandItem("open_ai", "AI Assistant", "Open the AI assistant panel", Icons.Default.AutoAwesome, onOpenAi),
            CommandItem("switch_theme", "Switch theme", "Change light / dark / system in Settings", Icons.Default.Settings, onSwitchTheme),
            CommandItem("open_favorites", "Favorites", "View your favorited notes", Icons.Default.Star, onOpenFavorites),
            CommandItem("archive_note", "Archive note", "Archive the current note from the editor", Icons.Default.Archive, onArchiveNote),
            CommandItem("open_settings", "Settings", "Open app settings", Icons.Default.Settings, onOpenSettings),
            CommandItem("open_exports", "Downloads & Exports", "Export notes to TXT/MD/HTML/PDF", Icons.Default.Download, onOpenExports)
        )
    }

    Dialog(onDismissRequest = onDismiss) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text("Command palette") },
                    navigationIcon = {
                        IconButton(onClick = onDismiss) {
                            Icon(Icons.Default.Close, contentDescription = "Close")
                        }
                    }
                )
            }
        ) { padding ->
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(padding)
            ) {
                items(commands, key = { it.id }) { cmd ->
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { cmd.action() }
                            .padding(horizontal = 16.dp, vertical = 12.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(cmd.icon, contentDescription = null)
                            Column(modifier = Modifier.padding(start = 12.dp)) {
                                Text(cmd.title, style = MaterialTheme.typography.bodyLarge)
                                Text(
                                    cmd.subtitle,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

private data class CommandItem(
    val id: String,
    val title: String,
    val subtitle: String,
    val icon: ImageVector,
    val action: () -> Unit
)
