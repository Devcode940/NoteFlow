package com.noteflow.ai.core.common

/**
 * Pure text statistics helpers (word/character counts, reading time,
 * previews). No Android dependencies so they are unit-testable on the JVM.
 */
object TextStats {

    /** Words are whitespace-separated tokens containing at least one letter or digit. */
    fun countWords(text: String): Int {
        if (text.isBlank()) return 0
        return text.split(Regex("\\s+")).count { token ->
            token.any { it.isLetterOrDigit() }
        }
    }

    fun countChars(text: String): Int = text.length

    /** Reading time in whole minutes at [wordsPerMinute]; 0 for empty text. */
    fun readingTimeMinutes(wordCount: Int, wordsPerMinute: Int = 200): Int {
        if (wordCount <= 0) return 0
        return maxOf(1, (wordCount + wordsPerMinute - 1) / wordsPerMinute)
    }

    fun readingTimeLabel(wordCount: Int): String {
        val minutes = readingTimeMinutes(wordCount)
        return if (minutes == 0) "0 min read" else "$minutes min read"
    }

    /** First meaningful lines of [content] with Markdown markers stripped, for list previews. */
    fun extractPreview(content: String, maxChars: Int = 160): String {
        val lines = content.lineSequence()
            .map { stripMarkdown(it).trim() }
            .filter { it.isNotEmpty() }
            .take(3)
            .joinToString(" ")
        return if (lines.length > maxChars) lines.take(maxChars).trimEnd() + "…" else lines
    }

    /**
     * Removes common Markdown syntax for plain-text contexts (previews,
     * search snippets, TXT export). Links keep their visible text.
     */
    fun stripMarkdown(text: String): String {
        var result = text
        result = result.replace(Regex("!\\[([^]]*)]\\([^)]*\\)"), "$1") // images -> alt text
        result = result.replace(Regex("\\[([^]]+)]\\([^)]*\\)"), "$1") // links -> text
        result = result.replace(Regex("^#{1,6}\\s+"), "") // headings
        result = result.replace(Regex("^>\\s?"), "") // blockquotes
        result = result.replace(Regex("^\\s*[-*+]\\s+\\[[ xX]]\\s+"), "") // checklist
        result = result.replace(Regex("^\\s*[-*+]\\s+"), "") // bullets
        result = result.replace(Regex("^\\s*\\d+[.)]\\s+"), "") // ordered lists
        result = result.replace("**", "").replace("__", "")
        result = result.replace("*", "").replace("_", "")
        result = result.replace("~~", "")
        result = result.replace("`", "")
        return result
    }

    /** Formats a word-goal progress fraction in [0,1] for progress bars. */
    fun goalProgress(wordCount: Int, goal: Int): Float {
        if (goal <= 0) return 0f
        return (wordCount.toFloat() / goal.toFloat()).coerceIn(0f, 1f)
    }
}
