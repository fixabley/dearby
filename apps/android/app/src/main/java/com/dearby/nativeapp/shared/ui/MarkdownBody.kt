package com.dearby.nativeapp.shared.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.dearby.nativeapp.shared.lib.MarkdownDocument

/** Renders a card body (markdown subset) as stacked text: headings, lists and paragraphs with inline bold/italic/links. */
@Composable fun MarkdownBody(source: String, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        MarkdownDocument.blocks(source).forEach { block ->
            when (block) {
                is MarkdownDocument.Block.Heading -> Text(MarkdownDocument.inline(block.text, Teal), fontWeight = FontWeight.Bold,
                    style = when (block.level) { 1 -> MaterialTheme.typography.headlineSmall; 2 -> MaterialTheme.typography.titleLarge; else -> MaterialTheme.typography.titleMedium })
                is MarkdownDocument.Block.ListBlock -> Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    block.items.forEachIndexed { index, item ->
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(if (block.ordered) "${index + 1}." else "•", color = Quiet, style = MaterialTheme.typography.bodyMedium)
                            Text(MarkdownDocument.inline(item, Teal), style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
                is MarkdownDocument.Block.Paragraph -> Text(buildAnnotatedString {
                    block.lines.forEachIndexed { index, line -> if (index > 0) append("\n"); append(MarkdownDocument.inline(line, Teal)) }
                }, style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}
