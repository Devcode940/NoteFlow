package com.noteflow.ai.core.common

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Checkbox
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.LinkInteractionListener
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import com.noteflow.ai.core.designsystem.CodeFontFamily

/**
 * Renders parsed Markdown with native Compose components. Because rendering
 * is native (never WebView/HTML), untrusted note content cannot execute
 * scripts — the parser output is the only input. Links are clickable and
 * restricted to http/https URLs by the parser.
 */
@Composable
fun MarkdownContent(markdown: String, modifier: Modifier = Modifier) {
    val blocks = remember(markdown) { MarkdownParser.parse(markdown) }
    val uriHandler = LocalUriHandler.current
    val linkListener = remember(uriHandler) {
        object : LinkInteractionListener {
            override fun onClick(link: LinkAnnotation) {
                if (link is LinkAnnotation.Url && MarkdownParser.isSafeUrl(link.url)) {
                    uriHandler.openUri(link.url)
                }
            }
        }
    }
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        blocks.forEach { block -> MarkdownBlock(block, linkListener) }
    }
}

@Composable
private fun MarkdownBlock(block: MarkdownParser.Block, linkListener: LinkInteractionListener) {
    when (block) {
        is MarkdownParser.Block.Heading -> Text(
            text = annotated(block.inlines, linkListener),
            style = when (block.level) {
                1 -> MaterialTheme.typography.displayMedium
                2 -> MaterialTheme.typography.headlineLarge
                3 -> MaterialTheme.typography.headlineMedium
                4 -> MaterialTheme.typography.titleLarge
                5 -> MaterialTheme.typography.titleMedium
                else -> MaterialTheme.typography.titleSmall
            },
            color = MaterialTheme.colorScheme.onBackground
        )
        is MarkdownParser.Block.Paragraph -> Text(
            text = annotated(block.inlines, linkListener),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onBackground
        )
        is MarkdownParser.Block.CodeBlock -> CodeBlockView(block)
        is MarkdownParser.Block.BlockQuote -> Row {
            Box(
                modifier = Modifier
                    .width(4.dp)
                    .height(48.dp)
                    .background(MaterialTheme.colorScheme.primary, RoundedCornerShape(2.dp))
            )
            Spacer(Modifier.width(12.dp))
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                block.blocks.forEach { MarkdownBlock(it, linkListener) }
            }
        }
        is MarkdownParser.Block.UnorderedList -> Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            block.items.forEach { item ->
                Row(verticalAlignment = Alignment.Top) {
                    Text("•  ", style = MaterialTheme.typography.bodyLarge)
                    Text(
                        text = annotated(item, linkListener),
                        style = MaterialTheme.typography.bodyLarge
                    )
                }
            }
        }
        is MarkdownParser.Block.OrderedList -> Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            block.items.forEachIndexed { index, item ->
                Row(verticalAlignment = Alignment.Top) {
                    Text("${index + 1}.  ", style = MaterialTheme.typography.bodyLarge)
                    Text(
                        text = annotated(item, linkListener),
                        style = MaterialTheme.typography.bodyLarge
                    )
                }
            }
        }
        is MarkdownParser.Block.Checklist -> Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            block.items.forEach { item ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    // Checklist state is display-only in preview; editing happens
                    // in the text editor where "- [ ]" can be toggled.
                    Checkbox(checked = item.checked, onCheckedChange = null)
                    Text(
                        text = annotated(item.inlines, linkListener),
                        style = MaterialTheme.typography.bodyLarge,
                        textDecoration = if (item.checked) TextDecoration.LineThrough else TextDecoration.None
                    )
                }
            }
        }
        is MarkdownParser.Block.Table -> TableView(block)
        MarkdownParser.Block.HorizontalRule -> HorizontalDivider(
            modifier = Modifier.padding(vertical = 8.dp),
            color = MaterialTheme.colorScheme.outlineVariant
        )
    }
}

@Composable
private fun CodeBlockView(block: MarkdownParser.Block.CodeBlock) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHighest)
            .padding(12.dp)
    ) {
        block.language?.let {
            Text(
                text = it,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(4.dp))
        }
        Text(
            text = block.code,
            style = MaterialTheme.typography.bodyMedium.copy(fontFamily = CodeFontFamily),
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

@Composable
private fun TableView(block: MarkdownParser.Block.Table) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerLow)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                .padding(8.dp)
        ) {
            block.header.forEach { cell ->
                Text(
                    text = cell,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f)
                )
            }
        }
        block.rows.forEach { row ->
            Row(modifier = Modifier.fillMaxWidth().padding(8.dp)) {
                row.forEach { cell ->
                    Text(
                        text = cell,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        }
    }
}

private fun annotated(
    inlines: List<MarkdownParser.Inline>,
    linkListener: LinkInteractionListener
): AnnotatedString = buildAnnotatedString {
    inlines.forEach { appendInline(it, linkListener) }
}

private fun AnnotatedString.Builder.appendInline(
    inline: MarkdownParser.Inline,
    linkListener: LinkInteractionListener
) {
    when (inline) {
        is MarkdownParser.Inline.Text -> append(inline.text)
        is MarkdownParser.Inline.Bold -> withStyle(SpanStyle(fontWeight = FontWeight.Bold)) {
            inline.inlines.forEach { appendInline(it, linkListener) }
        }
        is MarkdownParser.Inline.Italic -> withStyle(SpanStyle(fontStyle = FontStyle.Italic)) {
            inline.inlines.forEach { appendInline(it, linkListener) }
        }
        is MarkdownParser.Inline.Strikethrough -> withStyle(
            SpanStyle(textDecoration = TextDecoration.LineThrough)
        ) {
            inline.inlines.forEach { appendInline(it, linkListener) }
        }
        is MarkdownParser.Inline.Code -> withStyle(
            SpanStyle(
                fontFamily = CodeFontFamily,
                background = Color(0x14000000)
            )
        ) {
            append(inline.text)
        }
        is MarkdownParser.Inline.Link -> {
            // The parser only emits http/https links; double-check before linking.
            if (MarkdownParser.isSafeUrl(inline.url)) {
                withLink(LinkAnnotation.Url(inline.url, linkListener)) {
                    withStyle(
                        SpanStyle(
                            color = Color(0xFF6D28D9),
                            textDecoration = TextDecoration.Underline
                        )
                    ) {
                        append(inline.text)
                    }
                }
            } else {
                append(inline.text)
            }
        }
    }
}
