package com.noteflow.ai.feature.commandpalette

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
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
    Dialog(onDismissRequest = onDismiss) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text("Command palette") },
                    navigationIcon = {
                        androidx.compose.material3.IconButton(onClick = onDismiss) {
                            androidx.compose.material3.Icon(
                                androidx.compose.material.icons.Icons.Default.Close,
                                contentDescription = "Close"
                            )
                        }
                    }
                )
            }
        ) { padding ->
            Column(modifier = Modifier.fillMaxSize().padding(padding)) {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(4.dp)
                ) {
                    items(Command.entries, key = { it.id }) { cmd ->
                        androidx.compose.material3.ListItem(
                            leadingIcon = { Icon(cmd.icon, contentDescription = null) },
                            headline = { Text(cmd.title) },
                            supportingText = { Text(cmd.subtitle, style = MaterialTheme.typography.bodySmall) },
                            onClick = {
                                onDismiss()
                                when (cmd.id) {
                                    "create_note" -> onCreateNote()
                                    "search" -> onSearch()
                                    "create_folder" -> onCreateFolder()
                                    "open_ai" -> onOpenAi()
                                    "open_settings" -> onOpenSettings()
                                    "open_favorites" -> onOpenFavorites()
                                    "open_exports" -> onOpenExports()
                                    "switch_theme" -> onSwitchTheme()
                                    "archive_note" -> onArchiveNote()
                                }
                            }
                        )
                    }
                }
            }
        }
    }
}

private data class Command(
    val id: String,
    val title: String,
    val subtitle: String,
    val icon: androidx.compose.ui.graphics.vector.ImageVector
)

private object Command {
    val entries = listOf(
        Command("create_note", "New note", "Create a blank note", Icons.Default.Add),
        Command("search", "Search notes", "Search across titles and content", Icons.Default.Search),
        Command("create_folder", "New folder", "Organize notes into folders", Icons.Default.Folder),
        Command("open_ai", "AI Assistant", "Open the AI assistant panel", Icons.Default.AutoAwesome),
        Command("switch_theme", "Switch theme", "Toggle light / dark / system", Icons.Default.Settings),
        Command("open_favorites", "Favorites", "View your favorited notes", Icons.Default.Star),
        Command("archive_note", "Archive note", "Archive the current note", Icons.Default.Archive),
        Command("open_settings", "Settings", "Open app settings", Icons.Default.Settings),
        Command("open_exports", "Downloads & Exports", "Export notes to TXT/MD/HTML/PDF", Icons.Default.Download)
    )
}

private fun Icon(vector: androidx.compose.ui.graphics.vector.ImageVector, contentDescription: String?) =
    androidx.compose.material3.Icon(vector, contentDescription)
