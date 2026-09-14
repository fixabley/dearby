package io.fixabley.dearby.widgets.notice.noticecard

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.ui.res.painterResource
import androidx.compose.material3.ButtonDefaults
import io.fixabley.dearby.R
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import io.fixabley.dearby.shared.ui.buttons.PrimaryButton

/** Display-only composition; the caller owns saved state, callbacks and test identity. */
@Composable
internal fun NoticeCardSaveButton(saved: Boolean, organizationName: String, onSave: () -> Unit, modifier: Modifier = Modifier) {
    PrimaryButton(onClick = onSave, modifier = modifier) {
        Icon(painterResource(if (saved) R.drawable.ic_favorite_filled else R.drawable.ic_favorite), contentDescription = null)
        Spacer(Modifier.width(ButtonDefaults.IconSpacing))
        Text(if (saved) "저장됨 · $organizationName" else "$organizationName 저장")
    }
}
