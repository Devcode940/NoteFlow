package com.noteflow.ai.core.security

import com.noteflow.ai.core.common.HtmlExporter
import com.noteflow.ai.core.common.MarkdownParser

/**
 * Input-validation helpers for untrusted data (note contents, imported
 * files, AI payloads). Note contents are always treated as untrusted:
 * rendering is native (never HTML), exports escape everything, and sizes
 * are bounded before they reach the network.
 */
object SecurityUtils {

    const val MAX_NOTE_CHARS = 1_000_000
    const val MAX_IMPORT_BYTES = 5 * 1024 * 1024 // 5 MB
    const val MAX_TITLE_CHARS = 500
    const val MAX_TAG_CHARS = 64

    /** Truncates [text] to [maxChars] without splitting surrogate pairs. */
    fun limitLength(text: String, maxChars: Int): String {
        if (text.length <= maxChars) return text
        var end = maxChars
        if (end > 0 && text[end - 1].isHighSurrogate()) end--
        return text.substring(0, end)
    }

    fun isValidTitle(title: String): Boolean = title.length <= MAX_TITLE_CHARS

    fun isValidTag(tag: String): Boolean =
        tag.isNotBlank() && tag.length <= MAX_TAG_CHARS && tag.none { it.isISOControl() }

    /** Only http/https URLs may be rendered as clickable links. */
    fun isSafeUrl(url: String): Boolean = MarkdownParser.isSafeUrl(url)

    /** Escapes text for safe inclusion in generated HTML exports. */
    fun escapeHtml(text: String): String = HtmlExporter.escapeHtml(text)

    /** Rejects imports that are too large to process safely. */
    fun validateImportSize(byteCount: Long): Boolean = byteCount in 1..MAX_IMPORT_BYTES
}
