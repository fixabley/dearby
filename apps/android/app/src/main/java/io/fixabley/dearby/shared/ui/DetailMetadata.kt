package io.fixabley.dearby.shared.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.font.FontWeight
import io.fixabley.dearby.shared.ui.theme.Spacing

/** Domain-free hierarchy; action remains independently accessible and uses native controls. */
@Composable
internal fun DetailMetadata(icon: Painter, primary: String, secondary: String?,
    description: String, modifier: Modifier = Modifier, action: @Composable () -> Unit = {}) {
    Row(modifier.fillMaxWidth().semantics(mergeDescendants = true) {}, horizontalArrangement = Arrangement.spacedBy(Spacing.small),
        verticalAlignment = Alignment.Top) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Column(Modifier.weight(1f).clearAndSetSemantics { contentDescription = description },
            verticalArrangement = Arrangement.spacedBy(Spacing.extraSmall)) {
            Text(primary, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
            secondary?.takeIf { it.isNotBlank() }?.let {
                Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        action()
    }
}
