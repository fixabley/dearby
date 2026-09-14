package io.fixabley.dearby.widgets.noticecard.ui

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import io.fixabley.dearby.shared.ui.buttons.PrimaryButton

/** Display-only composition; the caller owns saved state, callbacks and test identity. */
@Composable
internal fun NoticeCardSaveButton(saved: Boolean, organizationName: String, onSave: () -> Unit, modifier: Modifier = Modifier) {
    PrimaryButton(onClick = onSave, modifier = modifier) {
        Text(if (saved) "저장됨 · $organizationName" else "$organizationName 저장", maxLines = 2)
    }
}
