package io.fixabley.dearby.shared.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import io.fixabley.dearby.shared.ui.theme.Spacing

internal enum class StatusKind { Neutral, Loading, Error }

/** Domain-free empty/loading/failure presentation; callers own state and retry actions. */
@Composable
internal fun StatusPanel(
    title: String,
    modifier: Modifier = Modifier,
    message: String? = null,
    kind: StatusKind = StatusKind.Neutral,
    action: (@Composable () -> Unit)? = null,
) {
    val colors = MaterialTheme.colorScheme
    Surface(modifier.fillMaxWidth(), shape = MaterialTheme.shapes.large,
        color = if (kind == StatusKind.Error) colors.errorContainer else colors.surfaceContainer,
        contentColor = if (kind == StatusKind.Error) colors.onErrorContainer else colors.onSurface) {
        Column(Modifier.padding(Spacing.extraLarge), verticalArrangement = Arrangement.spacedBy(Spacing.medium)) {
            if (kind == StatusKind.Loading) CircularProgressIndicator()
            Column(Modifier.semantics(mergeDescendants = true) { liveRegion = LiveRegionMode.Polite },
                verticalArrangement = Arrangement.spacedBy(Spacing.small)) {
                Text(title, Modifier.semantics { heading() }, style = MaterialTheme.typography.titleMedium)
                message?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
            }
            action?.invoke()
        }
    }
}
