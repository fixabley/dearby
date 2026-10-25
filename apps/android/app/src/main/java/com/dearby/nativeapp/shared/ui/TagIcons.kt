package com.dearby.nativeapp.shared.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.MenuBook
import androidx.compose.material.icons.automirrored.rounded.TrendingUp
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.Placeable
import androidx.compose.ui.layout.SubcomposeLayout
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

/** A tag as the UI needs it: its Korean [name] and the [nameEn] key that picks its icon. */
data class TagLabel(val name: String, val nameEn: String?)

/** Contract "분야·성격 태그와 아이콘" table (plus 공학 `engineering`, 웹·앱 `webApp`, 백엔드 `backend`, 게임 `game`, 오픈소스 `openSource` added later), keyed by `nameEn`; an unknown key (a tag an operator just made) is `Sell`. */
fun tagIcon(nameEn: String?): ImageVector = when (nameEn) {
    "startup" -> Icons.Rounded.Lightbulb
    "project" -> Icons.Rounded.Build
    "mentoring" -> Icons.Rounded.Forum
    "networking" -> Icons.Rounded.Hub
    "funding" -> Icons.Rounded.Payments
    "it" -> Icons.Rounded.Computer
    "webApp" -> Icons.Rounded.Devices
    "backend" -> Icons.Rounded.Dns
    "game" -> Icons.Rounded.SportsEsports
    "openSource" -> Icons.Rounded.Code
    "dataAi" -> Icons.Rounded.AutoAwesome
    "cloud" -> Icons.Rounded.Cloud
    "semiconductor" -> Icons.Rounded.Memory
    "engineering" -> Icons.Rounded.Engineering
    "mechanical" -> Icons.Rounded.PrecisionManufacturing
    "civilEngineering" -> Icons.Rounded.Domain
    "security" -> Icons.Rounded.Security
    "business" -> Icons.Rounded.BarChart
    "marketing" -> Icons.Rounded.Campaign
    "investing" -> Icons.AutoMirrored.Rounded.TrendingUp
    "volunteering" -> Icons.Rounded.VolunteerActivism
    "reading" -> Icons.AutoMirrored.Rounded.MenuBook
    "video" -> Icons.Rounded.Videocam
    else -> Icons.Rounded.Sell
}

/**
 * Discovery card and expanded activity rows (contract "태그 아이콘+라벨·분야 1개"): one line of icon + text chips, [field] first on mint
 * with teal text, then [traits] on the neutral container. Chips that do not fit are dropped from the end and counted in a trailing `+n`;
 * the line never wraps. Read once as every tag's text in order, the hidden ones too (unplaced measure-only chips stay out of the tree). Nothing
 * when there are no tags.
 */
@Composable fun TagChipRow(field: TagLabel?, traits: List<TagLabel>, modifier: Modifier = Modifier) {
    val tags = listOfNotNull(field) + traits
    if (tags.isEmpty()) return
    val chip: @Composable (Int) -> Unit = { i ->
        if (i == 0 && field != null) LabelChip(tags[i], MaterialTheme.colorScheme.primaryContainer, MaterialTheme.colorScheme.onPrimaryContainer)
        else LabelChip(tags[i], MaterialTheme.colorScheme.surfaceContainerHighest, MaterialTheme.colorScheme.onSurfaceVariant)
    }
    SubcomposeLayout(modifier.fillMaxWidth().clearAndSetSemantics { contentDescription = tags.joinToString(", ") { it.name } }) { constraints ->
        val loose = constraints.copy(minWidth = 0, minHeight = 0)
        val spacing = 6.dp.roundToPx()
        val chips = subcompose("chips") { tags.indices.forEach { chip(it) } }.map { it.measure(loose) }
        fun widthOf(n: Int) = chips.take(n).sumOf { it.width } + spacing * (n - 1).coerceAtLeast(0)
        var shown = chips.size
        var more: Placeable? = null
        // Drop chips from the end until the rest plus `+n` fit; the first chip always stays (its text ellipsizes when it alone is too wide).
        while (widthOf(shown) + (more?.let { it.width + spacing } ?: 0) > constraints.maxWidth && shown > 1) {
            shown--
            more = subcompose("more$shown") { MoreChip(chips.size - shown) }.single().measure(loose)
        }
        val firstMax = (constraints.maxWidth - (more?.let { it.width + spacing } ?: 0)).coerceAtLeast(0)
        val first = if (chips[0].width <= firstMax) chips[0] else subcompose("first") { chip(0) }.single().measure(loose.copy(maxWidth = firstMax))
        val kept = listOf(first) + chips.subList(1, shown)
        val row = kept + listOfNotNull(more)
        val height = row.maxOf { it.height }
        layout(constraints.maxWidth, height) {
            var x = 0
            row.forEach { it.placeRelative(x, (height - it.height) / 2); x += it.width + spacing }
        }
    }
}

@Composable private fun LabelChip(tag: TagLabel, container: Color, content: Color) = Surface(color = container, contentColor = content, shape = RoundedCornerShape(8.dp)) {
    Row(Modifier.padding(horizontal = 8.dp, vertical = 3.dp), horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(tagIcon(tag.nameEn), null, Modifier.size(14.dp))
        Text(tag.name, style = MaterialTheme.typography.labelMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/** The `+n` of [TagChipRow]. */
@Composable private fun MoreChip(count: Int) = Surface(color = MaterialTheme.colorScheme.surfaceContainerHighest, contentColor = MaterialTheme.colorScheme.onSurfaceVariant, shape = RoundedCornerShape(8.dp)) {
    Text("+$count", Modifier.padding(horizontal = 8.dp, vertical = 3.dp), style = MaterialTheme.typography.labelMedium, maxLines = 1)
}

/** A tag as icon + text (activity detail's `분야` and `얻을 수 있는 것` groups); not interactive, so a plain surface rather than a chip button. */
@Composable fun TagChip(tag: TagLabel) = Surface(color = MaterialTheme.colorScheme.surfaceContainer, shape = RoundedCornerShape(10.dp)) {
    Row(Modifier.padding(horizontal = 10.dp, vertical = 5.dp), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(tagIcon(tag.nameEn), null, Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(tag.name, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.labelMedium)
    }
}
