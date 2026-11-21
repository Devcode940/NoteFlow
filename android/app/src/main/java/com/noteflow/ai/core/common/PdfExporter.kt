package com.noteflow.ai.core.common

import android.graphics.pdf.PdfDocument
import android.os.ParcelFileDescriptor
import java.io.FileOutputStream
import java.util.Date
import java.util.Locale

/**
 * Exports notes to PDF using Android's PdfDocument API. Each note gets its
 * own page; long content is line-wrapped and truncated at the page bottom.
 */
object PdfExporter {

    private const val PAGE_WIDTH = 595f  // A4 width in points
    private const val PAGE_HEIGHT = 842f // A4 height in points
    private const val MARGIN = 40f
    private const val CONTENT_WIDTH = PAGE_WIDTH - 2 * MARGIN

    fun export(fileDescriptor: ParcelFileDescriptor, title: String, notes: List<NoteExport>) {
        val document = PdfDocument()
        try {
            for ((index, note) in notes.withIndex()) {
                val pageInfo = PdfDocument.PageInfo.Builder(
                    PAGE_WIDTH.toInt(), PAGE_HEIGHT.toInt(), index + 1
                ).build()
                val page = document.startPage(pageInfo)
                val canvas = page.canvas

                val paintTitle = android.graphics.Paint().apply {
                    textSize = 22f
                    isAntiAlias = true
                    color = 0xFF6D28D9.toInt()
                }
                val paintMeta = android.graphics.Paint().apply {
                    textSize = 11f
                    isAntiAlias = true
                    color = 0xFF6E6E73.toInt()
                }
                val paintBody = android.graphics.Paint().apply {
                    textSize = 12f
                    isAntiAlias = true
                    color = 0xFF1C1B1F.toInt()
                }

                var y = 50f
                canvas.drawText(title, MARGIN, y, paintTitle)
                y += 30f
                canvas.drawText("Note: ${note.title}", MARGIN, y, paintBody)
                y += 18f
                val dateStr = java.text.SimpleDateFormat("MMM d, yyyy HH:mm", Locale.getDefault())
                    .format(Date(note.updatedAt))
                canvas.drawText("Updated: $dateStr", MARGIN, y, paintBody)
                y += 24f
                canvas.drawLine(MARGIN, y, PAGE_WIDTH - MARGIN, y, paintBody)
                y += 20f

                for (line in wrapText(note.content, 110)) {
                    if (y > PAGE_HEIGHT - 40f) break
                    canvas.drawText(line, MARGIN, y, paintBody)
                    y += 16f
                }
                document.finishPage(page)
            }
            document.writeTo(FileOutputStream(fileDescriptor.fileDescriptor))
        } finally {
            document.close()
        }
    }

    private fun wrapText(text: String, maxChars: Int): List<String> {
        val result = mutableListOf<String>()
        for (paragraph in text.split("\n")) {
            if (paragraph.isEmpty()) {
                result.add("")
            } else if (paragraph.length <= maxChars) {
                result.add(paragraph)
            } else {
                var start = 0
                while (start < paragraph.length) {
                    result.add(paragraph.substring(start, minOf(start + maxChars, paragraph.length)))
                    start += maxChars
                }
            }
        }
        return result
    }

    data class NoteExport(val title: String, val content: String, val updatedAt: Long)
}
