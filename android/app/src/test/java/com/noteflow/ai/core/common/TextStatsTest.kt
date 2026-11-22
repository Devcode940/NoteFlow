package com.noteflow.ai.core.common

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Unit tests for TextStats. Uses the real top-level functions and the
 * ContentStats aggregate (the previous version called non-existent APIs and
 * did not compile).
 */
class TextStatsTest {

    @Test
    fun countsWordsAndChars() {
        assertEquals(2, TextStats.countWords("Hello world"))
        assertEquals(11, TextStats.countChars("Hello world"))
    }

    @Test
    fun emptyContent() {
        assertEquals(0, TextStats.countWords(""))
        assertEquals(0, TextStats.countChars(""))
    }

    @Test
    fun contentStatsAggregate() {
        val stats = TextStats.forContent("Hello world")
        assertEquals(2, stats.wordCount)
        assertEquals(11, stats.charCount)
        assertEquals("2 words · 11 chars", stats.summary)
        assertEquals(1, stats.readingTimeMinutes)
    }

    @Test
    fun emptyContentStats() {
        val stats = TextStats.forContent("")
        assertEquals(0, stats.wordCount)
        assertEquals(0, stats.charCount)
        assertEquals(0, stats.readingTimeMinutes)
        assertEquals("0 words · 0 chars", stats.summary)
    }

    @Test
    fun extractsPreview() {
        assertEquals("Hello world", TextStats.extractPreview("Hello world\nMore content"))
        assertEquals("Short", TextStats.extractPreview("Short"))
        assertEquals("", TextStats.extractPreview(""))
    }

    @Test
    fun readingTime() {
        // ~200 words per minute => 200 words = 1 minute
        val text = (1..200).joinToString(" ") { "word" }
        assertEquals(1, TextStats.readingTimeMinutes(TextStats.countWords(text)))
        assertEquals(0, TextStats.readingTimeMinutes(0))
    }

    @Test
    fun stripsMarkdown() {
        assertEquals("Hello world", TextStats.stripMarkdown("**Hello** *world*"))
        assertEquals("linked", TextStats.stripMarkdown("[linked](https://example.com)"))
    }
}
