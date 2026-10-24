package com.dearby.nativeapp.shared.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.unit.dp

/** A tag as the UI needs it: its Korean [name] and the [nameEn] key that picks its icon. */
data class TagLabel(val name: String, val nameEn: String?)

/** Contract "분야·성격 태그와 아이콘" table (plus the parent 분야 `engineering` 공학), keyed by `nameEn`; an unknown key (a tag an operator just made) is `Sell`. */
fun tagIcon(nameEn: String?): ImageVector = when (nameEn) {
    "startup" -> Icons.Rounded.Lightbulb
    "project" -> Icons.Rounded.Build
    "mentoring" -> Icons.Rounded.Forum
    "networking" -> Icons.Rounded.Hub
    "funding" -> Icons.Rounded.Payments
    "it" -> Icons.Rounded.Computer
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

/** One 분야 under the discovery card's logo: a 22dp mint circle with the teal icon, read as "분야: 이름". */
@Composable fun FieldTagBadge(tag: TagLabel) =
    Box(Modifier.size(22.dp).background(MaterialTheme.colorScheme.primaryContainer, CircleShape).clearAndSetSemantics { contentDescription = "분야: " + tag.name }, contentAlignment = Alignment.Center) {
        Icon(tagIcon(tag.nameEn), null, Modifier.size(14.dp), tint = MaterialTheme.colorScheme.onPrimaryContainer)
    }

/** 성격 as 16dp icons without text (discovery card and expanded activity rows), read once as "얻을 수 있는 것: a, b"; nothing when [tags] is empty. */
@Composable fun TraitIconRow(tags: List<TagLabel>, modifier: Modifier = Modifier) {
    if (tags.isEmpty()) return
    Row(modifier.clearAndSetSemantics { contentDescription = "얻을 수 있는 것: " + tags.joinToString(", ") { it.name } }, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        tags.forEach { Icon(tagIcon(it.nameEn), null, Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant) }
    }
}

/** A tag as icon + text (activity detail's `분야` and `얻을 수 있는 것` groups); not interactive, so a plain surface rather than a chip button. */
@Composable fun TagChip(tag: TagLabel) = Surface(color = MaterialTheme.colorScheme.surfaceContainer, shape = RoundedCornerShape(10.dp)) {
    Row(Modifier.padding(horizontal = 10.dp, vertical = 5.dp), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(tagIcon(tag.nameEn), null, Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(tag.name, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.labelMedium)
    }
}
