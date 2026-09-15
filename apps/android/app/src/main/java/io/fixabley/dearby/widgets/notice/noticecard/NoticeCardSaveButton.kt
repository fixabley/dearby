package io.fixabley.dearby.widgets.notice.noticecard

import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import io.fixabley.dearby.R

/** Saving is idempotent, not a toggle: removal stays in Favorites. */
@Composable
internal fun NoticeCardSaveButton(saved: Boolean, organizationName: String, onSave: () -> Unit, modifier: Modifier = Modifier) {
    FilledTonalIconButton(onClick = onSave, modifier = modifier.semantics {
        selected = saved
        stateDescription = if (saved) "저장됨" else "저장되지 않음"
    }) {
        Icon(painterResource(if (saved) R.drawable.ic_favorite_filled else R.drawable.ic_favorite),
            contentDescription = if (saved) "저장됨 · $organizationName" else "$organizationName 저장")
    }
}
