package com.dearby.nativeapp.shared.lib

import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.withLink
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration

/**
 * The markdown subset cards use (contract "템플릿"): headings `#`–`###`, bold, italic, bullet and numbered lists,
 * links (https, mailto, tel) and line breaks. Raw HTML stays text. Line based; never fails.
 */
object MarkdownDocument {
    sealed interface Block {
        data class Heading(val level: Int, val text: String) : Block
        data class ListBlock(val ordered: Boolean, val items: List<String>) : Block
        data class Paragraph(val lines: List<String>) : Block
    }
    private val heading = Regex("""^(#{1,3}) (.*)$""")
    private val bullet = Regex("""^\s*[-*+] (.*)$""")
    private val numbered = Regex("""^\s*\d+[.)] (.*)$""")
    private val inline = Regex("""(\*\*(.+?)\*\*)|(\*(.+?)\*)|(_(.+?)_)|(\[([^\]]+)]\(([^)\s]+)\))""")

    fun blocks(source: String): List<Block> {
        val result = mutableListOf<Block>()
        for (line in source.replace("\r\n", "\n").replace('\r', '\n').split('\n')) {
            val h = heading.find(line)
            val b = bullet.find(line)
            val n = numbered.find(line)
            val last = result.lastOrNull()
            when {
                h != null -> result += Block.Heading(h.groupValues[1].length, h.groupValues[2])
                b != null || n != null -> {
                    val ordered = n != null
                    val text = (b ?: n)!!.groupValues[1]
                    if (last is Block.ListBlock && last.ordered == ordered) result[result.lastIndex] = last.copy(items = last.items + text)
                    else result += Block.ListBlock(ordered, listOf(text))
                }
                line.isBlank() -> result += Block.Paragraph(emptyList())
                last is Block.Paragraph && last.lines.isNotEmpty() -> result[result.lastIndex] = last.copy(lines = last.lines + line)
                else -> result += Block.Paragraph(listOf(line))
            }
        }
        return result.filter { it !is Block.Paragraph || it.lines.isNotEmpty() }
    }

    /** Inline bold/italic/links; links outside https/mailto/tel are plain text. */
    fun inline(text: String, linkColor: androidx.compose.ui.graphics.Color): AnnotatedString = AnnotatedString.Builder().apply {
        var rest = text
        while (rest.isNotEmpty()) {
            val match = inline.find(rest) ?: run { append(rest); rest = ""; return@apply }
            append(rest.substring(0, match.range.first))
            val g = match.groupValues
            when {
                g[2].isNotEmpty() -> withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(inline(g[2], linkColor)) }
                g[4].isNotEmpty() -> withStyle(SpanStyle(fontStyle = FontStyle.Italic)) { append(inline(g[4], linkColor)) }
                g[6].isNotEmpty() -> withStyle(SpanStyle(fontStyle = FontStyle.Italic)) { append(inline(g[6], linkColor)) }
                g[8].isNotEmpty() && Regex("""^(https://|mailto:|tel:)""", RegexOption.IGNORE_CASE).containsMatchIn(g[9]) ->
                    withLink(LinkAnnotation.Url(g[9], TextLinkStyles(SpanStyle(color = linkColor, textDecoration = TextDecoration.Underline)))) { append(g[8]) }
                else -> append(match.value)
            }
            rest = rest.substring(match.range.last + 1)
        }
    }.toAnnotatedString()
}
