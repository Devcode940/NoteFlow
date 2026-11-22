package com.noteflow.ai.feature.editor

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicNone
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import com.noteflow.ai.core.common.MarkdownContent
import com.noteflow.ai.domain.model.EditorMode

/**
 * Note editor. The ViewModel owns the edited text; the screen keeps only a
 * TextFieldValue wrapper for cursor/selection state, seeded ONCE per note id
 * (the old LaunchedEffect(state.note) re-seeded on every emission and
 * clobbered in-progress edits — P1-6 fix).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NoteEditorScreen(
    noteId: String,
    onNoteCreated: (String) -> Unit,
    onNavigateBack: () -> Unit,
    onOpenAi: () -> Unit,
    viewModel: NoteEditorViewModel = hiltViewModel()
) {
    val note by viewModel.note.collectAsState()
    val title by viewModel.title.collectAsState()
    val content by viewModel.content.collectAsState()
    val stats by viewModel.stats.collectAsState()
    val isDirty by viewModel.isDirty.collectAsState()
    val message by viewModel.message.collectAsState()
    val createdNoteId by viewModel.createdNoteId.collectAsState()

    val context: Context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    val focusRequester = remember { FocusRequester() }
    val dictation = remember(context) { VoiceDictation(context) }
    var mode by rememberSaveable { mutableStateOf(EditorMode.EDIT) }
    var voiceListening by remember { mutableStateOf(false) }
    var editorValue by rememberSaveable(stateSaver = TextFieldValue.Saver) {
        mutableStateOf(TextFieldValue(""))
    }

    // Seed the editor once per note (fires when the note id becomes known).
    LaunchedEffect(note?.id) {
        val loaded = note ?: return@LaunchedEffect
        if (editorValue.text != loaded.content && !viewModel.isDirty.value) {
            editorValue = TextFieldValue(loaded.content, TextRange(loaded.content.length))
        }
    }

    // "new" route: replace it with the real note id once the note exists.
    LaunchedEffect(createdNoteId) {
        createdNoteId?.let { onNoteCreated(it) }
    }

    // Voice dictation lifecycle: destroy the recognizer on leave-composition (P1-8 fix).
    DisposableEffect(Unit) {
        onDispose { dictation.destroy() }
    }

    LaunchedEffect(message) {
        val text = message ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(text)
        viewModel.consumeMessage()
    }

    fun startDictation() {
        voiceListening = true
        val started = dictation.start(object : VoiceDictation.Listener {
            override fun onResult(text: String) {
                editorValue = insertText(editorValue, text)
                viewModel.onContentChange(editorValue.text)
                voiceListening = false
            }

            override fun onError(messageText: String) {
                viewModel.setMessage(messageText)
                voiceListening = false
            }
        })
        if (!started) voiceListening = false
    }

    val requestPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            startDictation()
        } else {
            viewModel.setMessage("Microphone permission is required for voice dictation")
        }
    }

    fun onMicClick() {
        if (voiceListening) {
            dictation.stop()
            voiceListening = false
        } else if (!dictation.isAvailable) {
            viewModel.setMessage("Speech recognition is not available on this device")
        } else if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO)
            != PackageManager.PERMISSION_GRANTED
        ) {
            requestPermission.launch(Manifest.permission.RECORD_AUDIO)
        } else {
            startDictation()
        }
    }

    Scaffold(snackbarHost = { SnackbarHost(snackbarHostState) }) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .onPreviewKeyEvent { event ->
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
            TopAppBar(
                title = {
                    OutlinedTextField(
                        value = title,
                        onValueChange = viewModel::onTitleChange,
                        placeholder = { Text("Note title") },
                        singleLine = true,
                        textStyle = MaterialTheme.typography.titleLarge,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text),
                        modifier = Modifier.fillMaxWidth()
                    )
                },
                navigationIcon = {
                    IconButton(onClick = {
                        viewModel.save()
                        onNavigateBack()
                    }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { mode = if (mode == EditorMode.PREVIEW) EditorMode.EDIT else EditorMode.PREVIEW }) {
                        Icon(
                            Icons.Default.Visibility,
                            contentDescription = if (mode == EditorMode.PREVIEW) "Back to editing" else "Preview"
                        )
                    }
                    IconButton(onClick = { mode = EditorMode.FOCUS }) {
                        Icon(Icons.Default.Fullscreen, contentDescription = "Focus mode")
                    }
                    IconButton(onClick = onOpenAi) {
                        Icon(Icons.Default.AutoAwesome, contentDescription = "AI Assistant")
                    }
                    IconButton(onClick = viewModel::save, enabled = isDirty) {
                        Icon(Icons.Default.Save, contentDescription = "Save now")
                    }
                }
            )

            val contentModifier = Modifier
                .fillMaxWidth()
                .weight(1f)

            when (mode) {
                EditorMode.EDIT -> Column(modifier = Modifier.fillMaxSize()) {
                    FormattingToolbar(
                        value = editorValue,
                        onValueChange = {
                            editorValue = it
                            viewModel.onContentChange(it.text)
                        },
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
                    )
                    EditorTextField(
                        value = editorValue,
                        onValueChange = {
                            editorValue = it
                            viewModel.onContentChange(it.text)
                        },
                        focusRequester = focusRequester,
                        modifier = contentModifier.padding(horizontal = 16.dp)
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
                    ) {
                        IconButton(onClick = ::onMicClick) {
                            Icon(
                                imageVector = if (voiceListening) Icons.Default.Mic else Icons.Default.MicNone,
                                contentDescription = if (voiceListening) "Stop dictation" else "Start voice dictation",
                                tint = if (voiceListening) MaterialTheme.colorScheme.error
                                else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Text(
                            text = stats.summary,
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = when {
                                isDirty -> "Unsaved changes"
                                note?.id == null -> "Loading…"
                                else -> "All changes saved"
                            },
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
                EditorMode.FOCUS -> Column(modifier = Modifier.fillMaxSize()) {
                    EditorTextField(
                        value = editorValue,
                        onValueChange = {
                            editorValue = it
                            viewModel.onContentChange(it.text)
                        },
                        focusRequester = focusRequester,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 32.dp, vertical = 24.dp)
                    )
                }
            }
        }
    }
}

/** Inserts [insertion] at the current cursor position of [value]. */
private fun insertText(value: TextFieldValue, insertion: String): TextFieldValue {
    val cursor = value.selection.start.coerceIn(0, value.text.length)
    val newText = value.text.substring(0, cursor) + insertion + value.text.substring(cursor)
    val newCursor = cursor + insertion.length
    return TextFieldValue(newText, TextRange(newCursor, newCursor))
}

@Composable
private fun EditorTextField(
    value: TextFieldValue,
    onValueChange: (TextFieldValue) -> Unit,
    focusRequester: FocusRequester,
    modifier: Modifier = Modifier
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier
            .fillMaxHeight()
            .focusRequester(focusRequester),
        singleLine = false,
        maxLines = Int.MAX_VALUE,
        textStyle = TextStyle(fontSize = 18.sp, lineHeight = 28.sp),
        placeholder = {
            if (value.text.isEmpty()) {
                Text(
                    text = "Start writing…",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                )
            }
        }
    )
}
