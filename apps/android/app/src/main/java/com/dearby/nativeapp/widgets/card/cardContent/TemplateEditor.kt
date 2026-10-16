package com.dearby.nativeapp.widgets.card.cardContent

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.RemoveCircleOutline
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.dearby.nativeapp.shared.ui.*

/** A data token the editor can show: what it writes (`{{id}}`) and what it shows (the current value). */
data class TemplateToken(val id: String, val label: String, val value: String)

/**
 * 렌더링 화면 편집기(contract "템플릿"): 본문을 블록(제목 1~3, 글, 목록, 번호 목록) 단위로 서식 그대로 보이며 고친다.
 * 데이터 토큰은 값이 채워진 칩으로 보이고 안을 고칠 수 없다(토큰이 깨지는 입력은 무시). 굵게·기울임·링크 표시는 원문 그대로 둔다.
 */
@Composable fun TemplateEditor(template: String, tokens: List<TemplateToken>, onChange: (String) -> Unit, modifier: Modifier = Modifier) {
    val blocks = remember(template) { EditorBlock.parse(template) }
    fun emit(next: List<EditorBlock>) = onChange(next.joinToString("\n") { it.markdown() })
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        blocks.forEachIndexed { index, block ->
            Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                StyleMenu(block.kind) { kind -> emit(blocks.toMutableList().also { it[index] = block.copy(kind = kind) }) }
                BasicTextField(block.text, { text -> if (TokenGuard.allowed(block.text, text)) emit(blocks.toMutableList().also { it[index] = block.copy(text = text) }) },
                    Modifier.weight(1f).padding(top = 10.dp), textStyle = block.style(), visualTransformation = TokenTransformation(tokens),
                    decorationBox = { inner -> Box { if (block.text.isEmpty()) Text(block.kind.placeholder, color = Quiet, style = block.style()); inner() } })
                IconButton({ emit(blocks.filterIndexed { i, _ -> i != index }) }, enabled = blocks.size > 1) { Icon(Icons.Outlined.RemoveCircleOutline, "줄 삭제", tint = Quiet) }
            }
        }
        TextButton({ emit(blocks + EditorBlock(BlockKind.PARAGRAPH, "")) }, contentPadding = PaddingValues(0.dp)) { Text("줄 추가") }
        Text("제목·목록은 서식 그대로 보이고, 데이터 칩은 고칠 수 없어요. 굵게는 **글**, 링크는 [글](https://…)로 적어요.", color = Quiet, style = MaterialTheme.typography.labelSmall)
    }
}

enum class BlockKind(val label: String, val placeholder: String) {
    H1("제목 1", "제목"), H2("제목 2", "제목"), H3("제목 3", "제목"), PARAGRAPH("글", "내용을 적어 주세요"), BULLET("목록", "항목"), NUMBER("번호 목록", "항목")
}

/** One editable line of the template. */
data class EditorBlock(val kind: BlockKind, val text: String) {
    fun markdown() = when (kind) {
        BlockKind.H1 -> "# $text"; BlockKind.H2 -> "## $text"; BlockKind.H3 -> "### $text"
        BlockKind.BULLET -> "- $text"; BlockKind.NUMBER -> "1. $text"; BlockKind.PARAGRAPH -> text
    }
    @Composable fun style(): TextStyle = when (kind) {
        BlockKind.H1 -> MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold)
        BlockKind.H2 -> MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
        BlockKind.H3 -> MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
        else -> MaterialTheme.typography.bodyMedium
    }
    companion object {
        /** Lines, not blocks: each list item and each paragraph line is its own editable block; blank lines are kept as empty paragraphs. */
        fun parse(template: String): List<EditorBlock> {
            val lines = template.replace("\r\n", "\n").split('\n')
            val result = lines.map { line ->
                when {
                    line.startsWith("### ") -> EditorBlock(BlockKind.H3, line.removePrefix("### "))
                    line.startsWith("## ") -> EditorBlock(BlockKind.H2, line.removePrefix("## "))
                    line.startsWith("# ") -> EditorBlock(BlockKind.H1, line.removePrefix("# "))
                    Regex("""^\s*[-*+] """).containsMatchIn(line) -> EditorBlock(BlockKind.BULLET, line.replaceFirst(Regex("""^\s*[-*+] """), ""))
                    Regex("""^\s*\d+[.)] """).containsMatchIn(line) -> EditorBlock(BlockKind.NUMBER, line.replaceFirst(Regex("""^\s*\d+[.)] """), ""))
                    else -> EditorBlock(BlockKind.PARAGRAPH, line)
                }
            }
            return result.ifEmpty { listOf(EditorBlock(BlockKind.PARAGRAPH, "")) }
        }
    }
}

@Composable private fun StyleMenu(kind: BlockKind, onPick: (BlockKind) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        TextButton({ open = true }, Modifier.widthIn(min = 56.dp), contentPadding = PaddingValues(horizontal = 6.dp)) { Text(kind.label, style = MaterialTheme.typography.labelSmall) }
        DropdownMenu(open, { open = false }) { BlockKind.entries.forEach { DropdownMenuItem({ Text(it.label) }, { open = false; onPick(it) }) } }
    }
}

/** Tokens `{{…}}` are shown as their values in teal; the offset mapping keeps the caret outside them. */
private class TokenTransformation(private val tokens: List<TemplateToken>) : VisualTransformation {
    override fun filter(text: AnnotatedString): TransformedText {
        val matches = TokenGuard.pattern.findAll(text.text).toList()
        if (matches.isEmpty()) return TransformedText(text, OffsetMapping.Identity)
        val builder = AnnotatedString.Builder()
        // originalStart → (transformedStart, originalLength, transformedLength)
        val spans = mutableListOf<Triple<Int, Int, Int>>()
        var cursor = 0
        for (match in matches) {
            builder.append(text.text.substring(cursor, match.range.first))
            val id = match.groupValues[1]
            val token = tokens.firstOrNull { it.id.equals(id, ignoreCase = true) }
            val shown = token?.value?.ifEmpty { "〔${token.label}〕" } ?: "〔$id〕"
            spans += Triple(match.range.first, match.value.length, shown.length)
            builder.withStyle(SpanStyle(color = Teal, fontWeight = FontWeight.SemiBold)) { append(shown) }
            cursor = match.range.last + 1
        }
        builder.append(text.text.substring(cursor))
        return TransformedText(builder.toAnnotatedString(), object : OffsetMapping {
            override fun originalToTransformed(offset: Int): Int {
                var delta = 0
                for ((start, original, shown) in spans) {
                    if (offset <= start) break
                    // Inside a token: the caret lands after the shown value.
                    if (offset < start + original) return start + delta + shown
                    delta += shown - original
                }
                return offset + delta
            }
            override fun transformedToOriginal(offset: Int): Int {
                var delta = 0
                for ((start, original, shown) in spans) {
                    val tStart = start + delta
                    if (offset <= tStart) break
                    if (offset < tStart + shown) return start + original
                    delta += shown - original
                }
                return offset - delta
            }
        })
    }
}

/** Rejects edits that break a token: every token in the old text must still be whole, or be removed entirely. */
object TokenGuard {
    val pattern = Regex("""\{\{\s*([a-zA-Z]+(?:[.:][^{}\s]+)?)\s*}}""")
    fun allowed(old: String, new: String): Boolean {
        val before = pattern.findAll(old).map { it.value }.toList()
        val after = pattern.findAll(new).map { it.value }.toList()
        if (before.isEmpty()) return !new.contains("{{") || after.isNotEmpty() || !old.contains("{{")
        // Tokens may only disappear whole; a partially edited token leaves stray braces behind.
        val stray = new.replace(pattern, "").contains("{{") || new.replace(pattern, "").contains("}}")
        return !stray && after.all { it in before }
    }
}
