package com.noteflow.ai.core.common

import kotlin.test.Test
import kotlin.test.assertEquals

class TextStatsTest {

    @Test
    fun countsWordsAndChars() {
        val stats = TextStats.forContent("Hello world")
        assertEquals(2, stats.wordCount)
        assertEquals(11, stats.charCount)
    }

    @Test
    fun emptyContent() {
        val stats = TextStats.forContent("")
        assertEquals(0, stats.wordCount)
        assertEquals(0, stats.charCount)
        assertEquals("0 words · 0 chars", stats.summary)
    }

    @Test
    fun extractsPreview() {
        assertEquals("Hello world", TextStats.extractPreview("Hello world\nMore content"))
        assertEquals("Short", TextStats.extractPreview("Short"))
        assertEquals("", TextStats.extractPreview(""))
    }

    @Test
    func readingTime() {
        // ~200 words per minute => 200 words = 1 minute
        val stats = TextStats.forContent(("word " * 200).trimEnd())
        assertEquals(1, stats.readingTimeMinutes)
    }

    @Test
    fun stripsMarkdown() {
        assertEquals("Hello world", TextStats.stripMarkdown("**Hello** *world*"))
    }
}
