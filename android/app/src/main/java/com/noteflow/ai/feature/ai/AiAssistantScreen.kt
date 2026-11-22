package com.noteflow.ai.feature.ai

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.AssistChip
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.noteflow.ai.domain.model.AiMessage
import com.noteflow.ai.domain.model.AiRole

/**
 * AI assistant. Quick actions and generators operate on the text box content
 * (or the typed prompt for generators); the result is written back into the
 * text box so it can be inserted into a note or continued as a conversation.
 * The host NavHost provides the screen title bar.
 */
@Composable
fun AiAssistantScreen(
    onOpenNote: (String) -> Unit,
    viewModel: AiAssistantViewModel = hiltViewModel()
) {
    val messages by viewModel.messages.collectAsState()
    val isGenerating by viewModel.isGenerating.collectAsState()
    val error by viewModel.error.collectAsState()
    val aiModel by viewModel.aiModel.collectAsState()
    val isSignedIn by viewModel.isSignedIn.collectAsState()
    val clipboard = LocalClipboardManager.current
    var input by remember { mutableStateOf("") }

    val send = {
        val message = input.trim()
        if (message.isNotBlank() && !isGenerating) {
            input = ""
            viewModel.addUserMessage(message)
            viewModel.streamReply(
                noteContext = null,
                model = aiModel,
                onComplete = { }
            )
        }
        Unit
    }

    Column(modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        if (!isSignedIn) {
            Text(
                text = "Sign in (Profile) to enable AI features.",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(vertical = 8.dp)
            )
        }

        // Quick actions: apply an AI transform to the text box content.
        Text("Quick actions", style = MaterialTheme.typography.titleSmall)
        LazyVerticalGrid(
            columns = GridCells.Fixed(3),
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(viewModel.quickActions, key = { it.key }) { action ->
                AssistChip(
                    onClick = {
                        viewModel.runQuickAction(
                            action = action,
                            text = input,
                            instructions = null,
                            model = aiModel,
                            onResult = { input = it }
                        )
                    },
                    label = { Text(action.label, maxLines = 1) }
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Generators: produce content from the text box as the prompt.
        Text("Generators", style = MaterialTheme.typography.titleSmall)
        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(viewModel.generators, key = { it.key }) { gen ->
                AssistChip(
                    onClick = {
                        viewModel.generate(
                            type = gen,
                            prompt = input,
                            noteContext = null,
                            model = aiModel,
                            onResult = { input = it }
                        )
                    },
                    label = { Text(gen.label, maxLines = 1) }
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Conversation (LazyColumn: recomposes only new rows while streaming — P2-3 fix).
        val listState = rememberLazyListState()
        LaunchedEffect(messages.size) {
            if (messages.isNotEmpty()) listState.animateScrollToItem(messages.size - 1)
        }
        LazyColumn(
            state = listState,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (messages.isEmpty() && !isGenerating) {
                item {
                    Text(
                        text = "Ask anything, or use a quick action on your text above.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            items(messages, key = { it.id }) { message ->
                AiMessageRow(
                    message = message,
                    onCopy = { clipboard.setText(AnnotatedString(message.content)) },
                    onInsert = { input = input + message.content }
                )
            }
            if (isGenerating) {
                item {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(modifier = Modifier.height(20.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Generating…", style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
            error?.let { message ->
                item {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Default.Error,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.error
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = message,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                    TextButton(onClick = viewModel::clearError) { Text("Dismiss") }
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Input
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = input,
                onValueChange = { input = it },
                placeholder = { Text("Type a message or text to transform…") },
                singleLine = false,
                maxLines = 4,
                modifier = Modifier.weight(1f),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                keyboardActions = KeyboardActions(onSend = { send() })
            )
            Spacer(modifier = Modifier.width(8.dp))
            IconButton(onClick = { send() }, enabled = !isGenerating) {
                Icon(Icons.Default.Send, contentDescription = "Send")
            }
            if (isGenerating) {
                IconButton(onClick = { viewModel.cancel() }) {
                    Icon(Icons.Default.Cancel, contentDescription = "Cancel generation")
                }
            }
        }
    }
}

@Composable
private fun AiMessageRow(
    message: AiMessage,
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
                    else MaterialTheme.colorScheme.surfaceContainerLow,
                    RoundedCornerShape(12.dp)
                )
                .padding(12.dp)
        ) {
            Text(
                text = message.content.ifBlank { "…" },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onBackground
            )
            if (!isUser && message.content.isNotBlank()) {
                Spacer(modifier = Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.End) {
                    IconButton(onClick = onCopy) {
                        Icon(Icons.Default.ContentCopy, contentDescription = "Copy reply")
                    }
                    IconButton(onClick = onInsert) {
                        Icon(Icons.Default.ContentPaste, contentDescription = "Insert into text box")
                    }
                }
            }
        }
    }
}
