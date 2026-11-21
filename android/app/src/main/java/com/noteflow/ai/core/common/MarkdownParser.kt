package com.noteflow.ai.core.common

/**
 * A small, safe Markdown parser.
 *
 * Security model: note contents are untrusted input. This parser never
 * produces HTML and never executes anything — it produces a typed block tree
 * that the Compose renderer draws with native components. Links are only
 * accepted for http/https URLs; anything else renders as plain text, which
 * prevents javascript: and other scheme-based attacks by construction.
 *
 * Supported: H1-H6, paragraphs, fenced code blocks, blockquotes, unordered
 * and ordered lists, checklists, tables, horizontal rules, and inline
 * bold/italic/strikethrough/code/links.
 */
object MarkdownParser {

    sealed interface Block {
        data class Heading(val level: Int, val inlines: List<Inline>) : Block
        data class Paragraph(val inlines: List<Inline>) : Block
        data class CodeBlock(val language: String?, val code: String) : Block
        data class BlockQuote(val blocks: List<Block>) : Block
        data class UnorderedList(val items: List<List<Inline>>) : Block
        data class OrderedList(val items: List<List<Inline>>) : Block
        data class ChecklistItem(val checked: Boolean, val inlines: List<Inline>)
        data class Checklist(val items: List<ChecklistItem>) : Block
        data class Table(val header: List<String>, val rows: List<List<String>>) : Block
        data object HorizontalRule : Block
    }

    sealed interface Inline {
        data class Text(val text: String) : Inline
        data class Bold(val inlines: List<Inline>) : Inline
        data class Italic(val inlines: List<Inline>) : Inline
        data class Strikethrough(val inlines: List<Inline>) : Inline
        data class Code(val text: String) : Inline
        data class Link(val text: String, val url: String) : Inline
    }

    private val headingRegex = Regex("^(#{1,6})\\s+(.*)$")
    private val hrRegex = Regex("^\\s*([-*_])(\\s*\\1){2,}\\s*$")
    private val fenceRegex = Regex("^```\\s*([A-Za-z0-9_-]*)\\s*$")
    private val unorderedRegex = Regex("^\\s*[-*+]\\s+(.*)$")
    private val orderedRegex = Regex("^\\s*\\d+[.)]\\s+(.*)$")
    private val checklistRegex = Regex("^\\s*[-*+]\\s+\\[([ xX])]\\s+(.*)$")
    private val tableSeparatorRegex = Regex("^\\s*\\|?[\\s:|-]+\\|?\\s*$")

    fun parse(markdown: String): List<Block> {
        val lines = markdown.replace("\r\n", "\n").split("\n")
        return parseLines(lines)
    }

    private fun parseLines(lines: List<String>): List<Block> {
        val blocks = mutableListOf<Block>()
        var i = 0
        while (i < lines.size) {
            val line = lines[i]
            val trimmed = line.trim()

            // Fenced code block
            val fence = fenceRegex.matchEntire(trimmed)
            if (fence != null) {
                val language = fence.groupValues[1].ifBlank { null }
                val code = StringBuilder()
                i++
                while (i < lines.size && !lines[i].trim().startsWith("```")) {
                    code.appendLine(lines[i])
                    i++
                }
                // Consume the closing fence if present.
                if (i < lines.size) i++
                blocks += Block.CodeBlock(language, code.toString().trimEnd('\n'))
                continue
            }

            if (trimmed.isEmpty()) {
                i++
                continue
            }

            // Heading
            headingRegex.matchEntire(trimmed)?.let { match ->
                blocks += Block.Heading(match.groupValues[1].length, parseInlines(match.groupValues[2]))
                i++
                continue
            }

            // Horizontal rule
            if (hrRegex.matches(trimmed)) {
                blocks += Block.HorizontalRule
                i++
                continue
            }

            // Blockquote: collect consecutive '>' lines and parse recursively.
            if (trimmed.startsWith(">")) {
                val quoteLines = mutableListOf<String>()
                while (i < lines.size && lines[i].trim().startsWith(">")) {
                    quoteLines += lines[i].trim().removePrefix(">").removePrefix(" ")
                    i++
                }
                blocks += Block.BlockQuote(parseLines(quoteLines))
                continue
            }

            // Table: header row followed by a separator row.
            if (trimmed.startsWith("|") && i + 1 < lines.size &&
                tableSeparatorRegex.matches(lines[i + 1]) && lines[i + 1].contains("-")
            ) {
                val header = splitTableRow(trimmed)
                val rows = mutableListOf<List<String>>()
                i += 2
                while (i < lines.size && lines[i].trim().startsWith("|")) {
                    rows += splitTableRow(lines[i].trim())
                    i++
                }
                blocks += Block.Table(header, rows)
                continue
            }

            // Checklist
            if (checklistRegex.matches(trimmed)) {
                val items = mutableListOf<Block.ChecklistItem>()
                while (i < lines.size) {
                    val match = checklistRegex.matchEntire(lines[i].trim()) ?: break
                    val checked = match.groupValues[1].equals("x", ignoreCase = true)
                    items += Block.ChecklistItem(checked, parseInlines(match.groupValues[2]))
                    i++
                }
                blocks += Block.Checklist(items)
                continue
            }

            // Unordered list
            if (unorderedRegex.matches(trimmed)) {
                val items = mutableListOf<List<Inline>>()
                while (i < lines.size) {
                    val match = unorderedRegex.matchEntire(lines[i].trim()) ?: break
                    items += parseInlines(match.groupValues[1])
                    i++
                }
                blocks += Block.UnorderedList(items)
                continue
            }

            // Ordered list
            if (orderedRegex.matches(trimmed)) {
                val items = mutableListOf<List<Inline>>()
                while (i < lines.size) {
                    val match = orderedRegex.matchEntire(lines[i].trim()) ?: break
                    items += parseInlines(match.groupValues[1])
                    i++
                }
                blocks += Block.OrderedList(items)
                continue
            }

            // Paragraph: join consecutive non-blank, non-special lines.
            val paragraph = StringBuilder(trimmed)
            i++
            while (i < lines.size) {
                val next = lines[i].trim()
                if (next.isEmpty() || isSpecialLine(next)) break
                paragraph.append(' ').append(next)
                i++
            }
            blocks += Block.Paragraph(parseInlines(paragraph.toString()))
        }
        return blocks
    }

    private fun isSpecialLine(line: String): Boolean =
        headingRegex.matches(line) || hrRegex.matches(line) ||
            line.startsWith(">") || line.startsWith("```") ||
            unorderedRegex.matches(line) || orderedRegex.matches(line) ||
            checklistRegex.matches(line) || line.startsWith("|")

    private fun splitTableRow(line: String): List<String> =
        line.trim().removePrefix("|").removeSuffix("|").split("|").map { it.trim() }

    /** Parses inline formatting. Unmatched markers are kept as literal text. */
    fun parseInlines(text: String): List<Inline> {
        val result = mutableListOf<Inline>()
        val buffer = StringBuilder()
        var i = 0

        fun flush() {
            if (buffer.isNotEmpty()) {
                result += Inline.Text(buffer.toString())
                buffer.clear()
            }
        }

        while (i < text.length) {
            when {
                // Inline code (no formatting inside)
                text[i] == '`' -> {
                    val end = text.indexOf('`', i + 1)
                    if (end > i) {
                        flush()
                        result += Inline.Code(text.substring(i + 1, end))
                        i = end + 1
                    } else {
                        buffer.append(text[i]); i++
                    }
                }
                // Bold
                text.startsWith("**", i) -> {
                    val end = text.indexOf("**", i + 2)
                    if (end > i + 2) {
                        flush()
                        result += Inline.Bold(parseInlines(text.substring(i + 2, end)))
                        i = end + 2
                    } else {
                        buffer.append(text[i]); i++
                    }
                }
                // Strikethrough
                text.startsWith("~~", i) -> {
                    val end = text.indexOf("~~", i + 2)
                    if (end > i + 2) {
                        flush()
                        result += Inline.Strikethrough(parseInlines(text.substring(i + 2, end)))
                        i = end + 2
                    } else {
                        buffer.append(text[i]); i++
                    }
                }
                // Italic
                text[i] == '*' || text[i] == '_' -> {
                    val marker = text[i]
                    val end = text.indexOf(marker, i + 1)
                    if (end > i + 1) {
                        flush()
                        result += Inline.Italic(parseInlines(text.substring(i + 1, end)))
                        i = end + 1
                    } else {
                        buffer.append(text[i]); i++
                    }
                }
                // Link: only http/https URLs are honored.
                text[i] == '[' -> {
                    val closeBracket = text.indexOf(']', i + 1)
                    val closeParen = if (closeBracket > i) text.indexOf(')', closeBracket + 1) else -1
                    if (closeBracket > i && closeParen > closeBracket && text.getOrNull(closeBracket + 1) == '(') {
                        val label = text.substring(i + 1, closeBracket)
                        val url = text.substring(closeBracket + 2, closeParen).trim()
                        flush()
                        if (isSafeUrl(url)) {
                            result += Inline.Link(label, url)
                        } else {
                            // Unsafe scheme: render the label as plain text, drop the URL.
                            result += Inline.Text(label)
                        }
                        i = closeParen + 1
                    } else {
                        buffer.append(text[i]); i++
                    }
                }
                // Escape sequence: \* renders as *
                text[i] == '\\' && i + 1 < text.length -> {
                    buffer.append(text[i + 1])
                    i += 2
                }
                else -> {
                    buffer.append(text[i]); i++
                }
            }
        }
        flush()
        return result
    }

    /** Only http/https URLs are considered safe to render as links. */
    fun isSafeUrl(url: String): Boolean {
        val lower = url.trim().lowercase()
        return lower.startsWith("http://") || lower.startsWith("https://")
    }
}
