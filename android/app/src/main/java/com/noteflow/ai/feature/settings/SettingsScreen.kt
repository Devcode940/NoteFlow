package com.noteflow.ai.feature.settings

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonContainer
import androidx.compose.material3.SegmentedButtonGroup
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.noteflow.ai.domain.model.EditorMode
import com.noteflow.ai.domain.model.ExportFormat
import com.noteflow.ai.domain.model.ThemeMode

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun SettingsScreen(
    onOpenSignIn: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsState()
    var showDialog by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp)
    ) {
        androidx.compose.material3.TopAppBar(
            title = { Text("Settings") },
            navigationIcon = {
                IconButton(onClick = { /* handled by host */ }) {
                    Icon(ArrowBack, contentDescription = "Back")
                }
            }
        )
        Spacer(modifier = Modifier.height(16.dp))

        // Account
        Text("Account", style = MaterialTheme.typography.titleMedium)
        ListItem(
            headline = { Text("Sign in / Sign out") },
            leadingIcon = {
                Icon(
                    if (state.isSignedIn) Icons.Default.PowerSettingsNew else Icons.Default.Login,
                    contentDescription = null
                )
            },
            onClick = {
                if (state.isSignedIn) {
                    viewModel.signOut()
                } else {
                    onOpenSignIn()
                }
            }
        )
        if (state.isSignedIn) {
            ListItem(
                headline = { Text(state.userEmail ?: "Signed in") },
                leadingIcon = { Icon(Icons.Default.Person, contentDescription = null) },
                enabled = false
            )
        }
        Spacer(modifier = Modifier.height(16.dp))

        // Appearance
        Text("Appearance", style = MaterialTheme.typography.titleMedium)
        SegmentedButtonGroup(
            selected = state.themeMode,
            onValueChange = { viewModel.onThemeChange(it) }
        ) {
            SegmentedButton(
                value = ThemeMode.LIGHT,
                onClick = { viewModel.onThemeChange(ThemeMode.LIGHT) }
            ) { Text("Light") }
            SegmentedButton(
                value = ThemeMode.DARK,
                onClick = { viewModel.onThemeChange(ThemeMode.DARK) }
            ) { Text("Dark") }
            SegmentedButton(
                value = ThemeMode.SYSTEM,
                onClick = { viewModel.onThemeChange(ThemeMode.SYSTEM) }
            ) { Text("System") }
        }
        Spacer(modifier = Modifier.height(16.dp))

        // Editor
        Text("Editor", style = MaterialTheme.typography.titleMedium)
        ListItem(
            headline = { Text("Default mode") },
            supportingText = { Text(state.defaultEditorMode.name) },
            onClick = { /* open editor mode picker */ }
        )
        ListItem(
            headline = { Text("Auto-save") },
            trailingContent = {
                Switch(
                    checked = state.autoSave,
                    onCheckedChange = viewModel::onAutoSaveChange
                )
            }
        )
        ListItem(
            headline = { Text("Word-count goal") },
            supportingText = { Text("${state.wordGoal} words") },
            onClick = { /* open slider */ }
        )
        Spacer(modifier = Modifier.height(16.dp))

        // AI
        Text("AI Assistant", style = MaterialTheme.typography.titleMedium)
        ListItem(
            headline = { Text("AI model") },
            supportingText = { Text(state.aiModel) },
            onClick = { /* open model picker */ }
        )
        Spacer(modifier = Modifier.height(16.dp))

        // Sync
        Text("Synchronization", style = MaterialTheme.typography.titleMedium)
        ListItem(
            headline = { Text("Background sync") },
            trailingContent = {
                Switch(
                    checked = state.syncEnabled,
                    onCheckedChange = viewModel::onSyncEnabledChange
                )
            }
        )
        ListItem(
            headline = { Text("Backend URL") },
            supportingText = { Text(state.backendUrl.ifBlank { "Default" }) },
            onClick = { /* open URL editor */ }
        )
        Spacer(modifier = Modifier.height(16.dp))

        // Export
        Text("Export", style = MaterialTheme.typography.titleMedium)
        ListItem(
            headline = { Text("Default format") },
            supportingText = { Text(state.defaultExportFormat.name) },
            onClick = { /* open format picker */ }
        )
        Spacer(modifier = Modifier.height(16.dp))

        // About
        Text("About", style = MaterialTheme.typography.titleMedium)
        ListItem(
            headline = { Text("About NoteFlow AI") },
            onClick = { /* open about */ }
        )
        ListItem(
            headline = { Text("Version") },
            supportingText = { Text("1.0.0") },
            enabled = false
        )
    }

    if (showDialog) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { showDialog = false },
            title = { Text("Sign out") },
            text = { Text("Sign out of your account?") },
            confirmButton = {
                androidx.compose.material3.TextButton(onClick = {
                    showDialog = false
                    viewModel.signOut()
                }) { Text("Sign out") }
            },
            dismissButton = {
                androidx.compose.material3.TextButton(onClick = { showDialog = false }) { Text("Cancel") }
            }
        )
    }
}

private fun Spacer(modifier: Modifier) = androidx.compose.foundation.layout.Spacer(modifier = modifier)
