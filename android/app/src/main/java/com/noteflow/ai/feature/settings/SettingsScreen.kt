package com.noteflow.ai.feature.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Logout
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Slider
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.noteflow.ai.domain.model.AppSettings
import com.noteflow.ai.domain.model.EditorMode
import com.noteflow.ai.domain.model.ExportFormat
import com.noteflow.ai.domain.model.ThemeMode

/**
 * Settings. The host NavHost provides the screen title bar; every picker is
 * a real, working dialog (no dead menu entries).
 */
@Composable
fun SettingsScreen(
    onOpenSignIn: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    var showEditorModePicker by remember { mutableStateOf(false) }
    var showWordGoalPicker by remember { mutableStateOf(false) }
    var showModelPicker by remember { mutableStateOf(false) }
    var showBackendUrlPicker by remember { mutableStateOf(false) }
    var showFormatPicker by remember { mutableStateOf(false) }
    var confirmSignOut by remember { mutableStateOf(false) }

    LaunchedEffect(state.message) {
        val message = state.message ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(message)
        viewModel.consumeMessage()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp)
    ) {
        SnackbarHost(snackbarHostState)

        SectionTitle("Account")
        SettingsItem(
            headline = if (state.isSignedIn) "Sign out" else "Sign in",
            supporting = state.userEmail,
            leading = {
                Icon(
                    if (state.isSignedIn) Icons.Default.Logout else Icons.Default.Person,
                    contentDescription = null
                )
            },
            onClick = { if (state.isSignedIn) confirmSignOut = true else onOpenSignIn() }
        )
        SectionSpacer()

        SectionTitle("Appearance")
        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
            ThemeMode.entries.forEachIndexed { index, mode ->
                SegmentedButton(
                    selected = state.settings.themeMode == mode,
                    onClick = { viewModel.onThemeChange(mode) },
                    shape = SegmentedButtonDefaults.itemShape(index, ThemeMode.entries.size)
                ) {
                    Text(mode.name.lowercase().replaceFirstChar { it.uppercase() })
                }
            }
        }
        SectionSpacer()

        SectionTitle("Editor")
        SettingsItem(
            headline = "Default mode",
            supporting = state.settings.defaultEditorMode.name.lowercase(),
            onClick = { showEditorModePicker = true }
        )
        ToggleItem(
            headline = "Auto-save",
            supporting = "Save edits automatically while typing",
            checked = state.settings.autoSave,
            onCheckedChange = viewModel::onAutoSaveChange
        )
        SettingsItem(
            headline = "Word-count goal",
            supporting = "${state.settings.wordGoal} words",
            onClick = { showWordGoalPicker = true }
        )
        SectionSpacer()

        SectionTitle("AI Assistant")
        SettingsItem(
            headline = "AI model",
            supporting = state.settings.aiModel,
            onClick = { showModelPicker = true }
        )
        SettingsItem(
            headline = "Backend URL",
            supporting = state.settings.backendUrl.ifBlank { "Default (built-in)" },
            onClick = { showBackendUrlPicker = true }
        )
        SectionSpacer()

        SectionTitle("Synchronization")
        ToggleItem(
            headline = "Background sync",
            supporting = "Sync with Supabase when signed in",
            checked = state.settings.syncEnabled,
            onCheckedChange = viewModel::onSyncEnabledChange
        )
        SectionSpacer()

        SectionTitle("Export")
        SettingsItem(
            headline = "Default format",
            supporting = state.settings.defaultExportFormat.name,
            onClick = { showFormatPicker = true }
        )
        SectionSpacer()

        SectionTitle("About")
        SettingsItem(headline = "NoteFlow AI", supporting = "Offline-first notes with AI assistance")
        SettingsItem(headline = "Version", supporting = "1.0.0")
        SectionSpacer()
    }

    if (confirmSignOut) {
        AlertDialog(
            onDismissRequest = { confirmSignOut = false },
            title = { Text("Sign out") },
            text = { Text("Sign out of your account? Local notes stay on this device.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmSignOut = false
                    viewModel.signOut()
                }) { Text("Sign out") }
            },
            dismissButton = {
                TextButton(onClick = { confirmSignOut = false }) { Text("Cancel") }
            }
        )
    }

    RadioPickerDialog(
        visible = showEditorModePicker,
        title = "Default editor mode",
        options = EditorMode.entries.map { it.name.lowercase().replaceFirstChar { c -> c.uppercase() } },
        selectedIndex = EditorMode.entries.indexOf(state.settings.defaultEditorMode),
        onDismiss = { showEditorModePicker = false },
        onPick = { index ->
            viewModel.onEditorModeChange(EditorMode.entries[index])
            showEditorModePicker = false
        }
    )

    SliderDialog(
        visible = showWordGoalPicker,
        title = "Word-count goal",
        value = state.settings.wordGoal,
        onDismiss = { showWordGoalPicker = false },
        onConfirm = {
            viewModel.onWordGoalChange(it)
            showWordGoalPicker = false
        }
    )

    TextPickerDialog(
        visible = showModelPicker,
        title = "AI model",
        initial = state.settings.aiModel,
        placeholder = "gemini-2.5-flash",
        onDismiss = { showModelPicker = false },
        onConfirm = {
            viewModel.onAiModelChange(it)
            showModelPicker = false
        }
    )

    TextPickerDialog(
        visible = showBackendUrlPicker,
        title = "AI backend URL",
        initial = state.settings.backendUrl,
        placeholder = "https://your-backend.example.com",
        onDismiss = { showBackendUrlPicker = false },
        onConfirm = {
            viewModel.onBackendUrlChange(it)
            showBackendUrlPicker = false
        }
    )

    RadioPickerDialog(
        visible = showFormatPicker,
        title = "Default export format",
        options = ExportFormat.entries.map { it.name },
        selectedIndex = ExportFormat.entries.indexOf(state.settings.defaultExportFormat),
        onDismiss = { showFormatPicker = false },
        onPick = { index ->
            viewModel.onExportFormatChange(ExportFormat.entries[index])
            showFormatPicker = false
        }
    )
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleMedium,
        modifier = Modifier.padding(vertical = 8.dp)
    )
}

@Composable
private fun SectionSpacer() {
    Spacer(modifier = Modifier.height(16.dp))
}

@Composable
private fun SettingsItem(
    headline: String,
    supporting: String? = null,
    leading: (@Composable () -> Unit)? = null,
    onClick: (() -> Unit)? = null
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(
                if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier
            )
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (leading != null) {
            leading()
            Spacer(modifier = Modifier.padding(end = 12.dp))
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(headline, style = MaterialTheme.typography.bodyLarge)
            if (supporting != null) {
                Text(
                    supporting,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun ToggleItem(
    headline: String,
    supporting: String?,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onCheckedChange(!checked) }
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(headline, style = MaterialTheme.typography.bodyLarge)
            if (supporting != null) {
                Text(
                    supporting,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
private fun RadioPickerDialog(
    visible: Boolean,
    title: String,
    options: List<String>,
    selectedIndex: Int,
    onDismiss: () -> Unit,
    onPick: (Int) -> Unit
) {
    if (!visible) return
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                options.forEachIndexed { index, option ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onPick(index) }
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(selected = index == selectedIndex, onClick = { onPick(index) })
                        Text(option)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@Composable
private fun TextPickerDialog(
    visible: Boolean,
    title: String,
    initial: String,
    placeholder: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    if (!visible) return
    var value by remember(title) { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = value,
                onValueChange = { value = it },
                placeholder = { Text(placeholder) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(value) }) { Text("Save") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@Composable
private fun SliderDialog(
    visible: Boolean,
    title: String,
    value: Int,
    onDismiss: () -> Unit,
    onConfirm: (Int) -> Unit
) {
    if (!visible) return
    var current by remember { mutableStateOf(value.toFloat()) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                Text("${current.toInt()} words", style = MaterialTheme.typography.bodyLarge)
                Slider(
                    value = current,
                    onValueChange = { current = it },
                    valueRange = 0f..10_000f
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(current.toInt()) }) { Text("Save") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}
