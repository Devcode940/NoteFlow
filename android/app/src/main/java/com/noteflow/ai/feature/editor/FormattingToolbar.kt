package com.noteflow.ai.feature.editor

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.FormatBold
import androidx.compose.material.icons.filled.FormatItalic
import androidx.compose.material.icons.filled.FormatListBulleted
import androidx.compose.material.icons.filled.FormatListNumbered
import androidx.compose.material.icons.filled.FormatQuote
import androidx.compose.material.icons.filled.FormatStrikethrough
import androidx.compose.material.icons.filled.HorizontalRule
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.TableChart
import androidx.compose.material.icons.filled.Title
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue

/**
 * Markdown formatting toolbar. All operations are pure transformations on the
 * editor's [TextFieldValue] — they insert real Markdown syntax at the cursor
 * or around the selection, so the document stays plain Markdown.
 */
@Composable
fun FormattingToolbar(
    value: TextFieldValue,
    onValueChange: (TextFieldValue) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.horizontalScroll(rememberScrollState())
    ) {
        ToolButton(Icons.Filled.Title, "Heading 1") {
            onValueChange(prefixLines(value, "# "))
        }
        ToolButton(Icons.Filled.FormatBold, "Bold") {
            onValueChange(wrapSelection(value, "**", "**", "bold text"))
        }
        ToolButton(Icons.Filled.FormatItalic, "Italic") {
            onValueChange(wrapSelection(value, "*", "*", "italic text"))
        }
        ToolButton(Icons.Filled.FormatStrikethrough, "Strikethrough") {
            onValueChange(wrapSelection(value, "~~", "~~", "struck text"))
        }
        ToolButton(Icons.Filled.Code, "Inline code") {
            onValueChange(wrapSelection(value, "`", "`", "code"))
        }
        ToolButton(Icons.Filled.FormatQuote, "Blockquote") {
            onValueChange(prefixLines(value, "> "))
        }
        ToolButton(Icons.Filled.FormatListBulleted, "Bullet list") {
            onValueChange(prefixLines(value, "- "))
        }
        ToolButton(Icons.Filled.FormatListNumbered, "Numbered list") {
            onValueChange(prefixLines(value, "1. "))
        }
        ToolButton(Icons.Filled.TableChart, "Table") {
            onValueChange(insertAtCursor(value, "\n| Header | Header |\n| --- | --- |\n| Cell | Cell |\n"))
        }
        ToolButton(Icons.Filled.Link, "Link") {
            onValueChange(wrapSelection(value, "[", "](https://)", "link text"))
        }
        ToolButton(Icons.Filled.HorizontalRule, "Horizontal rule") {
            onValueChange(insertAtCursor(value, "\n---\n"))
        }
    }
}

@Composable
private fun ToolButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    description: String,
    onClick: () -> Unit
) {
    IconButton(onClick = onClick) {
        Icon(icon, contentDescription = description)
    }
}

/** Wraps the selection (or inserts markers at the cursor) with [prefix]/[suffix]. */
internal fun wrapSelection(
    value: TextFieldValue,
    prefix: String,
    suffix: String,
    placeholder: String
): TextFieldValue {
    val text = value.text
    val selection = value.selection
    return if (selection.start != selection.end) {
        val selected = text.substring(selection.start, selection.end)
        val newText = text.substring(0, selection.start) + prefix + selected + suffix +
            text.substring(selection.end)
        val newCursor = selection.start + prefix.length + selected.length + suffix.length
        TextFieldValue(newText, TextRange(newCursor))
    } else {
        val cursor = selection.start
        val newText = text.substring(0, cursor) + prefix + placeholder + suffix + text.substring(cursor)
        val start = cursor + prefix.length
        TextFieldValue(newText, TextRange(start, start + placeholder.length))
    }
}

/** Prefixes every line touched by the selection (or the current line). */
internal fun prefixLines(value: TextFieldValue, prefix: String): TextFieldValue {
    val text = value.text
    val selection = value.selection
    val startLine = text.lastIndexOf('\n', selection.start - 1) + 1
    val endPos = if (selection.end > selection.start) selection.end - 1 else selection.start
    val endLine = text.indexOf('\n', endPos).let { if (it == -1) text.length else it }
    val block = text.substring(startLine, endLine)
    val prefixed = block.split("\n").joinToString("\n") { line ->
        if (line.isBlank()) line else prefix + line
    }
    val newText = text.substring(0, startLine) + prefixed + text.substring(endLine)
    val shift = prefixed.length - block.length
    return TextFieldValue(newText, TextRange(selection.start, selection.end + shift))
}

/** Inserts [insertion] at the cursor position. */
internal fun insertAtCursor(value: TextFieldValue, insertion: String): TextFieldValue {
    val cursor = value.selection.start
    val newText = value.text.substring(0, cursor) + insertion + value.text.substring(cursor)
    return TextFieldValue(newText, TextRange(cursor + insertion.length))
}
