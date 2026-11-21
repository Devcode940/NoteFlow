package com.noteflow.ai.core.common

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.assertFalse

class MarkdownParserTest {

    @Test
    fun parsesHeadings() {
        val blocks = MarkdownParser.parse("# Hello\n## World")
        assertEquals(2, blocks.size)
        assertEquals(1, (blocks[0] as MarkdownParser.Block.Heading).level)
        assertEquals("Hello", (blocks[0] as MarkdownParser.Block.Heading).inlines.joinToString(""))
    }

    @Test
    fun parsesBoldAndItalic() {
        val blocks = MarkdownParser.parse("**bold** and *italic*")
        val para = blocks[0] as MarkdownParser.Block.Paragraph
        val text = para.inlines.joinToString("")
        assertEquals("bold and italic", text)
    }

    @Test
    fun onlyHttpsLinksAreSafe() {
        assertTrue(MarkdownParser.isSafeUrl("https://example.com"))
        assertTrue(MarkdownParser.isSafeUrl("http://example.com"))
        assertFalse(MarkdownParser.isSafeUrl("javascript:alert(1)"))
        assertFalse(MarkdownParser.isSafeUrl("data:text/html,<script>"))
        assertFalse(MarkdownParser.isSafeUrl("file:///etc/passwd"))
        assertFalse(MarkdownParser.isSafeUrl("vbscript:msgbox(1)"))
    }

    @Test
    fun parsesCodeBlock() {
        val blocks = MarkdownParser.parse("```kotlin\nval x = 1\n```")
        val code = blocks[0] as MarkdownParser.Block.CodeBlock
        assertEquals("kotlin", code.language)
        assertEquals("val x = 1\n", code.code)
    }

    @Test
    fun parsesChecklist() {
        val blocks = MarkdownParser.parse("- [ ] task one\n- [x] task two")
        val list = blocks[0] as MarkdownParser.Block.Checklist
        assertEquals(2, list.items.size)
        assertFalse(list.items[0].checked)
        assertTrue(list.items[1].checked)
    }

    @Test
    fun parsesTable() {
        val blocks = MarkdownParser.parse("| a | b |\n| --- | --- |\n| 1 | 2 |")
        val table = blocks[0] as MarkdownParser.Block.Table
        assertEquals(listOf("a", "b"), table.header)
        assertEquals(1, table.rows.size)
        assertEquals(listOf("1", "2"), table.rows[0])
    }

    @Test
    fun stripsUnsafeLinksFromRenderedText() {
        val blocks = MarkdownParser.parse("Click [here](javascript:alert(1))")
        val para = blocks[0] as MarkdownParser.Block.Paragraph
        val rendered = para.inlines.joinToString("")
        assertEquals("Click here", rendered)
    }
}
