package io.fixabley.dearby.shared.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.style.TextOverflow
import io.fixabley.dearby.shared.ui.theme.Spacing

/** A decorative native icon and compact value; accessibility always gets the complete fact. */
@Composable
internal fun MetadataRow(icon: Painter, value: String, description: String,
    modifier: Modifier = Modifier, maxLines: Int = Int.MAX_VALUE) {
    Row(modifier.clearAndSetSemantics { contentDescription = description },
        horizontalArrangement = Arrangement.spacedBy(Spacing.small)) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface,
            maxLines = maxLines, overflow = TextOverflow.Ellipsis)
    }
}
