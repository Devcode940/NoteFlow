package com.noteflow.ai.feature.ai

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.GridCells
import androidx.compose.foundation.lazy.LazyGrid
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.noteflow.ai.domain.model.AiGeneratorType
import com.noteflow.ai.domain.model.AiQuickAction
import com.noteflow.ai.domain.model.AiRole
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun AiAssistantScreen(
    onOpenNote: (String) -> Unit,
    viewModel: AiAssistantViewModel = hiltViewModel()
) {
    val messages by viewModel.messages.collectAsState()
    val isGenerating by viewModel.isGenerating.collectAsState()
    val error by viewModel.error.collectAsState()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var input by remember { mutableStateOf("") }
    var noteContext by remember { mutableStateOf<String?>(null) }
    val inputFocus = remember { FocusRequester() }

    val model = "gemini-2.5-flash"

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("AI Assistant") },
                navigationIcon = {
                    IconButton(onClick = { /* drawer handled by host */ }) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            // Context chip
            Row(
                modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Context: ${if (noteContext != null) "Selected note" else "None"}",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.width(8.dp))
                TextButton(onClick = { noteContext = null }) { Text("Clear") }
            }

            // Quick actions grid
            Column(modifier = Modifier.fillMaxWidth()) {
                Text("Quick actions", style = MaterialTheme.typography.titleSmall)
                LazyGrid(
                    columns = GridCells.Fixed(3),
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(viewModel.quickActions, key = { it.key }) { action ->
                        ActionChip(
                            label = action.label,
                            onClick = {
                                val text = noteContext ?: ""
                                if (text.isBlank()) {
                                    viewModel.setError( "Open a note first to use quick actions"
                                } else {
                                    viewModel.runQuickAction(
                                        action = action,
                                        text = text,
                                        instructions = null,
                                        model = model,
                                        onResult = { input = it }
                                    )
                                }
                            }
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Generators
            Column(modifier = Modifier.fillMaxWidth()) {
                Text("Generators", style = MaterialTheme.typography.titleSmall)
                LazyGrid(
                    columns = GridCells.Fixed(2),
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(viewModel.generators, key = { it.key }) { gen ->
                        ActionChip(
                            label = gen.label,
                            onClick = {
                                val text = noteContext ?: ""
                                if (text.isBlank()) {
                                    viewModel.setError( "Open a note first to use generators"
                                } else {
                                    viewModel.generate(
                                        type = gen,
                                        prompt = text,
                                        noteContext = null,
                                        model = model,
                                        onResult = { input = it }
                                    )
                                }
                            }
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Conversation
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
            ) {
                if (messages.isEmpty() && !isGenerating) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Text(
                            text = "Start a conversation with the AI assistant.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                messages.forEach { message ->
                    AiMessageRow(
                        message = message,
                        onCopy = { /* clipboard via host */ },
                        onInsert = { /* insert via host */ }
                    }
                }
                if (isGenerating) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator()
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Generating…", style = MaterialTheme.typography.bodyMedium)
                    }
                }
                error?.let {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Error, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = it,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Input
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                androidx.compose.material3.OutlinedTextField(
                    value = input,
                    onValueChange = { input = it },
                    placeholder = { Text("Ask the AI assistant…") },
                    singleLine = true,
                    modifier = Modifier.weight(1f).focusRequester(inputFocus),
                    keyboardOptions = androidx.compose.ui.text.input.KeyboardOptions(
                        imeAction = androidx.compose.ui.text.input.ImeAction.Send
                    ),
                    onKeyboardAction = { if (input.isNotBlank()) send() }
                )
                Spacer(modifier = Modifier.width(8.dp))
                IconButton(onClick = { send() }) {
                    Icon(Icons.Default.Send, contentDescription = "Send")
                }
                if (isGenerating) {
                    Spacer(modifier = Modifier.width(8.dp))
                    IconButton(onClick = { viewModel.cancel() }) {
                        Icon(Icons.Default.Cancel, contentDescription = "Cancel")
                    }
                }
            }
        }
    }

    // Handle async error display (StateFlow updates outside recomposition).
    LaunchedEffect(error) {
        // error is already displayed inline via collectAsState above.
    }

    // Keyboard shortcut: Ctrl+Enter to send.
    androidx.compose.foundation.layout.Box(Modifier.fillMaxSize()) {
        // handled by host's onPreviewKeyEvent if needed
    }
}

private fun AiAssistantScreen.send() {
    val message = input.trim()
    if (message.isBlank()) return
    input = ""
    viewModel.addUserMessage(message)
    viewModel.streamReply(
        noteContext = null,
        model = "gemini-2.5-flash",
        onComplete = { /* reply complete */ }
    )
}

@Composable
private fun AiMessageRow(
    message: com.noteflow.ai.domain.model.AiMessage,
    onCopy: () -> Unit,
    onInsert: () -> Unit
) {
    val isUser = message.role == AiRole.USER
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = 600.dp)
                .background(
                    if (isUser) MaterialTheme.colorScheme.primaryContainer
                    else MaterialTheme.colorScheme.surfaceContainerLow
                )
                .padding(12.dp)
        ) {
            Text(
                text = message.content,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onBackground
            )
            Spacer(modifier = Modifier.height(8.dp))
            if (!isUser) {
                Row(horizontalArrangement = Arrangement.End) {
                    IconButton(onClick = onCopy) {
                        Icon(Icons.Default.ContentCopy, contentDescription = "Copy")
                    }
                    IconButton(onClick = onInsert) {
                        Icon(Icons.Default.ContentPaste, contentDescription = "Insert into note")
                    }
                }
            }
        }
    }
}

@Composable
private fun ActionChip(label: String, onClick: () -> Unit) {
    androidx.compose.material3.Chip(
        onClick = onClick,
        label = { Text(label) },
        modifier = Modifier.fillMaxWidth()
    )
}
