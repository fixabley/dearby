package io.fixabley.dearby.shared.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.*
import io.fixabley.dearby.R
import io.fixabley.dearby.shared.ui.theme.Spacing

/** Caller supplies an already-validated destination; this component never opens a URL itself. */
@Composable
internal fun LinkCard(domain: String, address: String, onOpen: () -> Unit, modifier: Modifier = Modifier) {
    OutlinedCard(modifier.fillMaxWidth()) {
        Row(Modifier.padding(Spacing.medium), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.small)) {
            Text(domain, Modifier.weight(1f).clearAndSetSemantics { contentDescription = address },
                style = MaterialTheme.typography.bodyLarge)
            FilledTonalIconButton(onClick = onOpen) {
                Icon(painterResource(R.drawable.ic_open_in_new), contentDescription = "$domain 링크 열기")
            }
        }
    }
}
