package com.noteflow.ai.core.common

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/**
 * Generates standalone HTML and plain-text exports from Markdown.
 *
 * Security: all note content is escaped before interpolation. The only tags
 * in the output are the ones generated here, so untrusted Markdown can never
 * inject scripts or markup into the exported document (no XSS by construction).
 */
object HtmlExporter {

    /** Builds a complete, styled, standalone HTML document for a note. */
    fun markdownToHtml(title: String, markdown: String, createdAt: Long, updatedAt: Long): String {
        val body = MarkdownParser.parse(markdown).joinToString("\n") { blockToHtml(it) }
        val safeTitle = escapeHtml(title.ifBlank { "Untitled note" })
        val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US).apply {
            timeZone = TimeZone.getDefault()
        }
        return """
            <!DOCTYPE html>
            <html lang="en">
            <head>
            <meta charset="utf-8">
            <meta name="viewport" content="width=device-width, initial-scale=1">
            <title>$safeTitle</title>
            <style>
              body { font-family: -apple-system, 'Segoe UI', Roboto, sans-serif; max-width: 760px; margin: 40px auto; padding: 0 20px; color: #1a1a1a; line-height: 1.6; }
              h1 { font-size: 1.9em; border-bottom: 2px solid #7c3aed; padding-bottom: 8px; }
              h2 { font-size: 1.5em; } h3 { font-size: 1.25em; }
              code { background: #f3f4f6; padding: 2px 6px; border-radius: 4px; font-family: 'SF Mono', Consolas, monospace; font-size: 0.9em; }
              pre { background: #1f2937; color: #f9fafb; padding: 16px; border-radius: 8px; overflow-x: auto; }
              pre code { background: none; padding: 0; color: inherit; }
              blockquote { border-left: 4px solid #7c3aed; margin: 16px 0; padding: 4px 16px; color: #4b5563; background: #f9fafb; }
              table { border-collapse: collapse; width: 100%; margin: 16px 0; }
              th, td { border: 1px solid #d1d5db; padding: 8px 12px; text-align: left; }
              th { background: #f3f4f6; }
              hr { border: none; border-top: 1px solid #d1d5db; margin: 24px 0; }
              .meta { color: #6b7280; font-size: 0.85em; margin-bottom: 24px; }
              a { color: #7c3aed; }
            </style>
            </head>
            <body>
            <h1>$safeTitle</h1>
            <p class="meta">Created ${dateFormat.format(Date(createdAt))} &middot; Updated ${dateFormat.format(Date(updatedAt))}</p>
            $body
            </body>
            </html>
        """.trimIndent()
    }

    /** Builds a plain-text export (title header + stripped Markdown). */
    fun buildPlainText(title: String, markdown: String): String {
        val header = title.ifBlank { "Untitled note" }
        val body = markdown.lineSequence()
            .joinToString("\n") { TextStats.stripMarkdown(it) }
            .trim()
        return "$header\n${"=".repeat(header.length)}\n\n$body\n"
    }

    /** Builds a Markdown export, ensuring the title appears as an H1. */
    fun buildMarkdown(title: String, markdown: String): String {
        val trimmed = markdown.trim()
        val firstLine = trimmed.lineSequence().firstOrNull()?.trim().orEmpty()
        return if (firstLine.startsWith("#")) {
            trimmed + "\n"
        } else {
            "# ${title.ifBlank { "Untitled note" }}\n\n$trimmed\n"
        }
    }

    /** Makes a title safe to use as a file name (no path separators or control chars). */
    fun sanitizeFileName(title: String, fallback: String = "note"): String {
        val cleaned = title.trim()
            .replace(Regex("[\\\\/:*?\"<>|\\p{Cntrl}]"), "")
            .replace(Regex("\\s+"), "-")
            .trim('-', '.')
        return cleaned.ifBlank { fallback }
    }

    private fun blockToHtml(block: MarkdownParser.Block): String = when (block) {
        is MarkdownParser.Block.Heading ->
            "<h${block.level}>${inlinesToHtml(block.inlines)}</h${block.level}>"
        is MarkdownParser.Block.Paragraph ->
            "<p>${inlinesToHtml(block.inlines)}</p>"
        is MarkdownParser.Block.CodeBlock ->
            "<pre><code>${escapeHtml(block.code)}</code></pre>"
        is MarkdownParser.Block.BlockQuote ->
            "<blockquote>${block.blocks.joinToString("\n") { blockToHtml(it) }}</blockquote>"
        is MarkdownParser.Block.UnorderedList ->
            "<ul>${block.items.joinToString("") { "<li>${inlinesToHtml(it)}</li>" }}</ul>"
        is MarkdownParser.Block.OrderedList ->
            "<ol>${block.items.joinToString("") { "<li>${inlinesToHtml(it)}</li>" }}</ol>"
        is MarkdownParser.Block.Checklist ->
            "<ul>${block.items.joinToString("") {
                val box = if (it.checked) "&#9746;" else "&#9744;"
                "<li>$box ${inlinesToHtml(it.inlines)}</li>"
            }}</ul>"
        is MarkdownParser.Block.Table -> {
            val head = "<tr>${block.header.joinToString("") { "<th>${escapeHtml(it)}</th>" }}</tr>"
            val rows = block.rows.joinToString("") { row ->
                "<tr>${row.joinToString("") { "<td>${escapeHtml(it)}</td>" }}</tr>"
            }
            "<table>$head$rows</table>"
        }
        MarkdownParser.Block.HorizontalRule -> "<hr>"
    }

    private fun inlinesToHtml(inlines: List<MarkdownParser.Inline>): String =
        inlines.joinToString("") { inline ->
            when (inline) {
                is MarkdownParser.Inline.Text -> escapeHtml(inline.text)
                is MarkdownParser.Inline.Bold -> "<strong>${inlinesToHtml(inline.inlines)}</strong>"
                is MarkdownParser.Inline.Italic -> "<em>${inlinesToHtml(inline.inlines)}</em>"
                is MarkdownParser.Inline.Strikethrough -> "<del>${inlinesToHtml(inline.inlines)}</del>"
                is MarkdownParser.Inline.Code -> "<code>${escapeHtml(inline.text)}</code>"
                is MarkdownParser.Inline.Link ->
                    "<a href=\"${escapeHtml(inline.url)}\">${escapeHtml(inline.text)}</a>"
            }
        }

    /** Escapes the five HTML-significant characters. */
    fun escapeHtml(text: String): String = buildString(text.length) {
        for (ch in text) {
            when (ch) {
                '&' -> append("&amp;")
                '<' -> append("&lt;")
                '>' -> append("&gt;")
                '"' -> append("&quot;")
                '\'' -> append("&#39;")
                else -> append(ch)
            }
        }
    }
}
