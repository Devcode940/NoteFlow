package com.noteflow.ai.core.common

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class HtmlExporterTest {

    @Test
    fun escapesHtml() {
        assertEquals("&lt;script&gt;alert(1)&lt;/script&gt;", HtmlExporter.escapeHtml("<script>alert(1)</script>"))
        assertEquals("&amp;", HtmlExporter.escapeHtml("&"))
    }

    @Test
    fun sanitizeFileNameRemovesPathSeparators() {
        assertEquals("note", HtmlExporter.sanitizeFileName("../etc/passwd"))
        assertEquals("my-note", HtmlExporter.sanitizeFileName("my:note"))
    }

    @Test
    fun plainTextExportEscapesContent() {
        val html = HtmlExporter.markdownToHtml("Title", "content <script>", 0, 0)
        assertFalse(html.contains("<script>"))
        assertTrue(html.contains("&lt;script&gt;"))
    }
}
