package io.fixabley.dearby.widgets.notice.noticecard

import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.text
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.style.LineBreak
import androidx.compose.ui.unit.dp

/** Source field first, whitespace boundaries second. No place-name inference or inserted newlines.
 * FlowRow measures each word against the whole available width, keeping 2관 / 세미나실(5층)
 * together when they fit. A single oversized token may soft-wrap to avoid horizontal overflow.
 * Keep the original field as one accessibility text, rather than reading words individually.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun CardPlaceLine(value: String, modifier: Modifier = Modifier) {
    val words = remember(value) { value.trim().split(Regex("\\s+")).filter(String::isNotEmpty) }
    FlowRow(modifier.clearAndSetSemantics { text = AnnotatedString(value) },
        horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        words.forEach { word ->
            Text(word, style = MaterialTheme.typography.bodyMedium.copy(
                lineBreak = LineBreak.Paragraph.copy(
                    strictness = LineBreak.Strictness.Strict,
                    wordBreak = LineBreak.WordBreak.Phrase)))
        }
    }
}
