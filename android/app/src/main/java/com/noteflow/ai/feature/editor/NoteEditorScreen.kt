package com.noteflow.ai.feature.editor

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts.RequestPermission
import androidx.compose.foundation.fillMaxSize
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.weight
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.ctrlPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.noteflow.ai.core.common.MarkdownContent
import com.noteflow.ai.core.common.TextStats
import kotlinx.coroutines.launch

@Composable
fun NoteEditorScreen(
    noteId: String,
    onNoteCreated: (String) -> Unit,
    onNavigateBack: () -> Unit,
    onOpenAi: () -> Unit,
    viewModel: NoteEditorViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsState()
    val stats by viewModel.stats.collectAsState()
    val context: Context = LocalContext.current
    val scope = rememberCoroutineScope()
    var editorValue by remember { mutableStateOf(TextFieldValue("")) }
    var mode by remember { mutableStateOf(EditorMode.EDIT) }
    var voiceListening by remember { mutableStateOf(false) }

    val focusRequester = remember { FocusRequester() }
    val dictation = remember(context) { VoiceDictation(context) }
    val requestPermission = rememberLauncherForActivityResult(RequestPermission()) { granted ->
        if (granted) {
            startDictation()
        } else {
            viewModel.setMessage( "Microphone permission required for voice dictation"
        }
    }

    LaunchedEffect(state.note) {
        val note = state.note
        if (note != null) {
            editorValue = TextFieldValue(
                text = note.content,
                selection = TextRange(note.content.length)
            )
        }
    }

    fun startDictation() {
        voiceListening = true
        dictation.start(object : VoiceDictation.Listener {
            override fun onResult(text: String) {
                editorValue = insertText(editorValue, text)
                viewModel.insertVoiceText(text)
                voiceListening = false
            }
            override fun onError(message: String) {
                viewModel.setMessage( message
                voiceListening = false
            }
        })
    }

    Box(
        modifier = Modifier.fillMaxSize().onPreviewKeyEvent { event ->
            when {
                event.ctrlPressed && event.key == Key.Enter -> {
                    viewModel.save()
                    true
                }
                event.ctrlPressed && event.key == Key.E -> {
                    mode = EditorMode.PREVIEW
                    true
                }
                event.ctrlPressed && event.key == Key.F -> {
                    mode = EditorMode.FOCUS
                    true
                }
                else -> false
            }
        }
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            TopAppBar(
                title = {
                    Text(
                        text = state.note?.title?.ifBlank { "Untitled note" } ?: "Untitled note",
                        style = MaterialTheme.typography.titleLarge
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { mode = EditorMode.PREVIEW }) {
                        Icon(Icons.Default.ContentPaste, contentDescription = "Preview")
                    }
                    IconButton(onClick = { mode = EditorMode.FOCUS }) {
                        Icon(Icons.Default.Fullscreen, contentDescription = "Focus mode")
                    }
                    IconButton(onClick = onOpenAi) {
                        Icon(Icons.Default.AutoAwesome, contentDescription = "AI Assistant")
                    }
                    IconButton(onClick = viewModel::save) {
                        Icon(Icons.Default.Save, contentDescription = "Save")
                    }
                }
            )
            val contentModifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .padding(horizontal = 16.dp)

            when (mode) {
                EditorMode.EDIT -> Column(
                    modifier = Modifier.fillMaxSize()
                ) {
                    FormattingToolbar(
                        value = editorValue,
                        onValueChange = { editorValue = it; viewModel.onContentChange(it.text) },
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
                    )
                    EditorTextField(
                        value = editorValue,
                        onValueChange = { editorValue = it; viewModel.onContentChange(it.text) },
                        focusRequester = focusRequester,
                        modifier = contentModifier
                    )
                    VoiceButton(
                        listening = voiceListening,
                        onClick = {
                            if (voiceListening) {
                                dictation.stop()
                                voiceListening = false
                            } else {
                                if (context.checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
                                    requestPermission.launch(Manifest.permission.RECORD_AUDIO)
                                } else {
                                    startDictation()
                                }
                            }
                        }
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                        horizontalArrangement = androidx.compose.foundation.layout.Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = stats.summary,
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = "Auto-saving",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                EditorMode.PREVIEW -> Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp)
                        .verticalScroll(rememberScrollState())
                ) {
                    MarkdownContent(
                        markdown = editorValue.text,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                EditorMode.FOCUS -> Column(
                    modifier = Modifier.fillMaxSize()
                ) {
                    EditorTextField(
                        value = editorValue,
                        onValueChange = { editorValue = it; viewModel.onContentChange(it.text) },
                        focusRequester = focusRequester,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 64.dp, vertical = 32.dp)
                            .background(
                                MaterialTheme.colorScheme.surfaceContainerHighest
                            )
                    )
                }
            }
        }
    }
}

/** Inserts [insertion] at the current cursor position of [value]. */
private fun insertText(value: TextFieldValue, insertion: String): TextFieldValue {
    val cursor = value.selection.start
    val newText = value.text.substring(0, cursor) + insertion + value.text.substring(cursor)
    val newCursor = cursor + insertion.length
    return TextFieldValue(newText, TextRange(newCursor, newCursor))
}

@Composable
private fun VoiceButton(
    listening: Boolean,
    onClick: () -> Unit
) {
    IconButton(onClick = onClick) {
        Icon(
            imageVector = if (listening) Icons.Default.Mic else Icons.Default.MicNone,
            contentDescription = if (listening) "Listening…" else "Start voice dictation",
            tint = if (listening) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun EditorTextField(
    value: TextFieldValue,
    onValueChange: (TextFieldValue) -> Unit,
    focusRequester: FocusRequester,
    modifier: Modifier = Modifier
) {
    var placeholderVisible by remember { mutableStateOf(value.text.isEmpty()) }
    LaunchedEffect(value) {
        placeholderVisible = value.text.isEmpty()
    }
    androidx.compose.material3.OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier
            .fillMaxHeight()
            .focusRequester(focusRequester),
        singleLine = false,
        maxLines = Int.MAX_VALUE,
        textStyle = TextStyle(fontSize = 18.sp, lineHeight = 28.sp),
        placeholder = {
            if (placeholderVisible) {
                Text(
                    text = "Start writing…",
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                )
            }
        }
    )
}

enum class EditorMode { EDIT, PREVIEW, FOCUS }
