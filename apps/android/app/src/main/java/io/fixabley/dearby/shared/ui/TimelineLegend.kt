package io.fixabley.dearby.shared.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.fixabley.dearby.shared.ui.theme.Spacing

@Composable
internal fun TimelineLegend() {
    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.small), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(12.dp).background(MaterialTheme.colorScheme.primary, MaterialTheme.shapes.extraSmall))
        Text("활동", style = MaterialTheme.typography.labelSmall)
        Box(Modifier.size(12.dp).background(MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.75f), MaterialTheme.shapes.extraSmall))
        Text("내 일정", style = MaterialTheme.typography.labelSmall)
    }
}
