package com.noteflow.ai.core.common

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Regression tests for the SSE stream format (P1-5 fix). The backend sends a
 * strict JSON envelope; the parser must never append raw JSON fragments to
 * chat output.
 */
class SseParserTest {

    @Test
    fun parsesStartEvent() {
        assertEquals(SseParser.Event.Start, SseParser.parse("""data: {"type":"start"}"""))
    }

    @Test
    fun parsesDeltaText() {
        val event = SseParser.parse("""data: {"type":"delta","text":"Hello"}""")
        assertEquals(SseParser.Event.Delta("Hello"), event)
    }

    @Test
    fun parsesErrorEvent() {
        val event = SseParser.parse("""data: {"type":"error","message":"Rate limited"}""")
        assertEquals(SseParser.Event.Error("Rate limited"), event)
    }

    @Test
    fun parsesDoneMarker() {
        assertEquals(SseParser.Event.Done, SseParser.parse("data: [DONE]"))
    }

    @Test
    fun ignoresNonDataLinesAndComments() {
        assertNull(SseParser.parse(": keep-alive"))
        assertNull(SseParser.parse(""))
        assertNull(SseParser.parse("event: ping"))
    }

    @Test
    fun neverSurfacesRawJsonFragments() {
        // A malformed payload must be dropped, not appended as text.
        assertNull(SseParser.parse("""data: {"type":"delta" """))
        assertNull(SseParser.parse("data: not-json"))
        assertNull(SseParser.parse("""data: {"unknown":"shape"}"""))
    }

    @Test
    fun urlSafetyStopsDangerousSchemes() {
        assertTrue(MarkdownParser.isSafeUrl("https://example.com"))
        assertTrue(MarkdownParser.isSafeUrl("http://example.com"))
        assertFalse(MarkdownParser.isSafeUrl("javascript:alert(1)"))
        assertFalse(MarkdownParser.isSafeUrl("data:text/html,<script>"))
        assertFalse(MarkdownParser.isSafeUrl("file:///etc/passwd"))
    }
}
