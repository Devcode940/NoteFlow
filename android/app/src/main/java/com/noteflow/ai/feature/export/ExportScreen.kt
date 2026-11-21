package com.noteflow.ai.feature.export

import android.content.Intent
import android.net.Uri
import android.os.ParcelFileDescriptor
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts.CreateDocument
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonContainer
import androidx.compose.material3.SegmentedButtonGroup
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
import com.noteflow.ai.core.common.HtmlExporter
import com.noteflow.ai.core.common.PdfExporter
import com.noteflow.ai.domain.model.ExportFormat
import com.noteflow.ai.domain.model.Note
import com.noteflow.ai.domain.model.NoteFilter
import com.noteflow.ai.domain.model.NoteFilterType
import com.noteflow.ai.domain.model.SortBy
import com.noteflow.ai.domain.model.SortOrder
import com.noteflow.ai.domain.repository.NoteRepository
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@HiltViewModel
class ExportViewModel @Inject constructor(
    private val noteRepository: NoteRepository
) : ViewModel() {
    private val _notes = MutableStateFlow<List<Note>>(emptyList())
    val notes: StateFlow<List<Note>> = _notes.asStateFlow()

    init {
        viewModelScope.launch {
            noteRepository.observeNotes(
                NoteFilter(type = NoteFilterType.ALL),
                SortBy.UPDATED_AT,
                SortOrder.DESC
            ).collect { _notes.value = it }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun ExportScreen(
    onOpenNote: (String) -> Unit,
    viewModel: ExportViewModel = hiltViewModel()
) {
    val notes by viewModel.notes.collectAsState()
    var format by remember { mutableStateOf(ExportFormat.MARKDOWN) }
    var exporting by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }

    val context = androidx.compose.ui.platform.LocalContext.current
    val createDocument = rememberLauncherForActivityResult(CreateDocument()) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        exporting = true
        message = null
        val content = buildExportedContent(notes, format)
        context.contentResolver.openFileDescriptor(uri, "w")?.use { fd ->
            try {
                when (format) {
                    ExportFormat.PDF -> {
                        val exports = notes.map { PdfExporter.NoteExport(it.title, it.content, it.updatedAt) }
                        PdfExporter.export(ParcelFileDescriptor.dup(fd.fileDescriptor), "NoteFlow Export", exports)
                    }
                    else -> {
                        val out = java.io.OutputStreamWriter(java.io.FileOutputStream(fd.fileDescriptor))
                        out.write(content)
                        out.close()
                    }
                }
                message = "Exported ${notes.size} notes as ${format.name}"
            } catch (e: Exception) {
                message = "Export failed: ${e.message}"
            } finally {
                exporting = false
            }
        } ?: run {
            exporting = false
            message = "Could not open file for writing"
        }
    }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        androidx.compose.material3.TopAppBar(
            title = { Text("Downloads & Exports") },
            navigationIcon = {
                IconButton(onClick = { /* handled by host */ }) {
                    androidx.compose.material3.Icon(
                        androidx.compose.material.icons.Icons.Default.ArrowBack,
                        contentDescription = "Back"
                    )
                }
            }
        )
        androidx.compose.foundation.layout.Spacer(modifier = Modifier.height(16.dp))

        Text("Export format", style = MaterialTheme.typography.titleMedium)
        SegmentedButtonGroup(
            selected = format,
            onValueChange = { format = it }
        ) {
            SegmentedButton(value = ExportFormat.TXT, onClick = { format = ExportFormat.TXT }) {
                Text("TXT")
            }
            SegmentedButton(value = ExportFormat.MARKDOWN, onClick = { format = ExportFormat.MARKDOWN }) {
                Text("Markdown")
            }
            SegmentedButton(value = ExportFormat.HTML, onClick = { format = ExportFormat.HTML }) {
                Text("HTML")
            }
            SegmentedButton(value = ExportFormat.PDF, onClick = { format = ExportFormat.PDF }) {
                Text("PDF")
            }
        }

        androidx.compose.foundation.layout.Spacer(modifier = Modifier.height(24.dp))
        Text("Export all ${notes.size} notes", style = MaterialTheme.typography.titleMedium)

        message?.let {
            androidx.compose.material3.Text(
                text = it,
                color = if (it.startsWith("Export failed")) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
            )
            androidx.compose.foundation.layout.Spacer(modifier = Modifier.height(8.dp))
        }

        androidx.compose.material3.Button(
            onClick = {
                val intent = Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
                    addCategory(Intent.CATEGORY_OPENABLE)
                    type = "*/*"
                    val ext = when (format) {
                        ExportFormat.PDF -> "pdf"
                        ExportFormat.HTML -> "html"
                        ExportFormat.MARKDOWN -> "md"
                        ExportFormat.TXT -> "txt"
                    }
                    putExtra(Intent.EXTRA_TITLE, "NoteFlow Export.$ext")
                }
                createDocument.launch(intent)
            },
            enabled = notes.isNotEmpty() && !exporting
        ) {
            if (exporting) {
                androidx.compose.material3.CircularProgressIndicator()
            } else {
                Text("Export all notes")
            }
        }

        androidx.compose.foundation.layout.Spacer(modifier = Modifier.height(16.dp))
        Text("Notes", style = MaterialTheme.typography.titleMedium)
        LazyColumn(modifier = Modifier.fillMaxSize().weight(1f)) {
            items(notes, key = { it.id }) { note ->
                androidx.compose.material3.ListItem(
                    headline = { Text(note.title.ifBlank { "Untitled note" }) },
                    supportingText = { Text(note.content.take(60)) },
                    onClick = { onOpenNote(note.id) }
                )
            }
        }
    }
}

private fun buildExportedContent(notes: List<Note>, format: ExportFormat): String {
    val dateFmt = java.text.SimpleDateFormat("MMM d, yyyy HH:mm", java.util.Locale.getDefault())
    return when (format) {
        ExportFormat.TXT -> notes.joinToString("\n\n---\n\n") { note ->
            "Title: ${note.title}\nUpdated: ${dateFmt.format(java.util.Date(note.updatedAt))}\n\n${note.content}"
        }
        ExportFormat.MARKDOWN -> notes.joinToString("\n\n---\n\n") { note ->
            "# ${note.title}\n\n_Updated: ${dateFmt.format(java.util.Date(note.updatedAt))}_\n\n${note.content}"
        }
        ExportFormat.HTML -> notes.joinToString("\n") { note ->
            HtmlExporter.markdownToHtml(
                title = note.title.ifBlank { "Untitled" },
                markdown = note.content,
                createdAt = note.createdAt,
                updatedAt = note.updatedAt
            )
        }
        ExportFormat.PDF -> "" // handled by PdfExporter directly
    }
}

private fun Spacer(modifier: Modifier) = androidx.compose.foundation.layout.Spacer(modifier = modifier)
