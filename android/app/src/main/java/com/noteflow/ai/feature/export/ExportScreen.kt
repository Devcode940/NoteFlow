package com.noteflow.ai.feature.export

import android.net.Uri
import android.os.ParcelFileDescriptor
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.clickable
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.noteflow.ai.core.common.HtmlExporter
import com.noteflow.ai.core.common.Logger
import com.noteflow.ai.core.common.TextStats
import com.noteflow.ai.domain.model.ExportFormat
import com.noteflow.ai.domain.model.Note
import com.noteflow.ai.domain.model.NoteFilter
import com.noteflow.ai.domain.model.NoteFilterType
import com.noteflow.ai.domain.model.SortBy
import com.noteflow.ai.domain.model.SortOrder
import com.noteflow.ai.domain.repository.NoteRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.FileOutputStream
import java.io.OutputStreamWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject

@HiltViewModel
class ExportViewModel @Inject constructor(
    noteRepository: NoteRepository
) : ViewModel() {
    val notes: StateFlow<List<Note>> = noteRepository
        .observeNotes(
            NoteFilter(type = NoteFilterType.ALL),
            SortBy.UPDATED_AT,
            SortOrder.DESC
        )
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
}

/** Export notes to TXT / Markdown / HTML / PDF via the Storage Access Framework. */
@Composable
fun ExportScreen(
    onOpenNote: (String) -> Unit,
    viewModel: ExportViewModel = hiltViewModel()
) {
    val notes by viewModel.notes.collectAsState()
    var format by remember { mutableStateOf(ExportFormat.MARKDOWN) }
    var exporting by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val context = androidx.compose.ui.platform.LocalContext.current

    val createDocument = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument(mimeTypeFor(format))
    ) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        exporting = true
        message = null
        scope.launch {
            val result = runCatching {
                withContext(Dispatchers.IO) {
                    writeExport(context, uri, notes, format)
                }
            }
            exporting = false
            result.fold(
                onSuccess = { message = "Exported ${notes.size} notes as ${format.name}" },
                onFailure = {
                    Logger.e("ExportScreen", "Export failed", it)
                    message = "Export failed. Please try again."
                }
            )
        }
    }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Text("Export format", style = MaterialTheme.typography.titleMedium)
        Spacer(modifier = Modifier.height(8.dp))
        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
            ExportFormat.entries.forEachIndexed { index, exportFormat ->
                SegmentedButton(
                    selected = format == exportFormat,
                    onClick = { format = exportFormat },
                    shape = SegmentedButtonDefaults.itemShape(index, ExportFormat.entries.size)
                ) {
                    Text(
                        when (exportFormat) {
                            ExportFormat.MARKDOWN -> "Markdown"
                            else -> exportFormat.name
                        }
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))
        Text("Export all ${notes.size} notes", style = MaterialTheme.typography.titleMedium)

        message?.let {
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = it,
                style = MaterialTheme.typography.bodySmall,
                color = if (it.startsWith("Export failed")) {
                    MaterialTheme.colorScheme.error
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                }
            )
        }

        Spacer(modifier = Modifier.height(16.dp))
        Button(
            onClick = {
                exporting = true
                createDocument.launch(fileNameFor(format))
            },
            enabled = notes.isNotEmpty() && !exporting
        ) {
            if (exporting) {
                CircularProgressIndicator(modifier = Modifier.height(20.dp))
            } else {
                Text("Export all notes")
            }
        }

        Spacer(modifier = Modifier.height(16.dp))
        Text("Notes", style = MaterialTheme.typography.titleMedium)
        Spacer(modifier = Modifier.height(8.dp))
        LazyColumn(modifier = Modifier.fillMaxSize()) {
            items(notes, key = { it.id }) { note ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onOpenNote(note.id) }
                        .padding(vertical = 12.dp)
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = note.title.ifBlank { "Untitled note" },
                            style = MaterialTheme.typography.bodyLarge,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = TextStats.extractPreview(note.content, 80),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }
    }
}

private suspend fun writeExport(context: android.content.Context, uri: Uri, notes: List<Note>, format: ExportFormat) {
    val resolver = context.contentResolver
    resolver.openFileDescriptor(uri, "w")?.use { fd ->
        when (format) {
            ExportFormat.PDF -> {
                val exports = notes.map {
                    com.noteflow.ai.core.common.PdfExporter.NoteExport(it.title, it.content, it.updatedAt)
                }
                PdfExporter.export(ParcelFileDescriptor.dup(fd.fileDescriptor), "NoteFlow Export", exports)
            }
            else -> {
                OutputStreamWriter(FileOutputStream(fd.fileDescriptor)).use { out ->
                    out.write(buildExportedContent(notes, format))
                }
            }
        }
    } ?: throw IllegalStateException("Could not open file for writing")
}

private fun mimeTypeFor(format: ExportFormat): String = when (format) {
    ExportFormat.PDF -> "application/pdf"
    ExportFormat.HTML -> "text/html"
    ExportFormat.MARKDOWN -> "text/markdown"
    ExportFormat.TXT -> "text/plain"
}

private fun fileNameFor(format: ExportFormat): String = when (format) {
    ExportFormat.PDF -> "NoteFlow Export.pdf"
    ExportFormat.HTML -> "NoteFlow Export.html"
    ExportFormat.MARKDOWN -> "NoteFlow Export.md"
    ExportFormat.TXT -> "NoteFlow Export.txt"
}

private fun buildExportedContent(notes: List<Note>, format: ExportFormat): String {
    val dateFmt = SimpleDateFormat("MMM d, yyyy HH:mm", Locale.getDefault())
    return when (format) {
        ExportFormat.TXT -> notes.joinToString("\n\n---\n\n") { note ->
            "Title: ${note.title}\nUpdated: ${dateFmt.format(Date(note.updatedAt))}\n\n${note.content}"
        }
        ExportFormat.MARKDOWN -> notes.joinToString("\n\n---\n\n") { note ->
            "# ${note.title.ifBlank { "Untitled" }}\n\n_Updated: ${dateFmt.format(Date(note.updatedAt))}_\n\n${note.content}"
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
