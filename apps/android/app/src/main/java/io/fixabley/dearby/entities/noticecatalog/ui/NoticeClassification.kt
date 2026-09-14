package io.fixabley.dearby.entities.noticecatalog.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import io.fixabley.dearby.entities.noticecatalog.model.Notice

@Composable
internal fun NoticeClassification(
    notice: Notice,
    contextNames: String,
    modifier: Modifier = Modifier,
    maxLines: Int = Int.MAX_VALUE,
    overflow: TextOverflow = TextOverflow.Clip,
) {
    Text(listOf(notice.categorySummary, contextNames).filter { it.isNotEmpty() }.joinToString(" · "),
        modifier, style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = maxLines, overflow = overflow)
}
