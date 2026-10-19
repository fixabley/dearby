package com.dearby.nativeapp.pages.profile

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import com.dearby.nativeapp.shared.ui.CenteredMessage
import com.dearby.nativeapp.shared.ui.DearbyFieldError
import com.dearby.nativeapp.shared.ui.DearbyTopBar
import com.dearby.nativeapp.shared.ui.MessageAction

/**
 * 개인정보 동의 (contract "개인정보 동의", 2026-10-19): shown full screen before the first profile step and whenever the server says
 * consent is required. A [CenteredMessage] (contract "비스크롤 화면 배치·CTA"): lock symbol, title, explanation, the two required rows as
 * Material 3 `ListItem`s with a `Checkbox` (contract "폼은 플랫폼 기본 컴포넌트") and `보기` opening the web document (the route layer launches
 * the browser), then the full-width CTA at the bottom.
 */
@Composable fun ConsentPage(consenting: Boolean, error: String?, openPrivacy: () -> Unit, openTerms: () -> Unit, agree: () -> Unit, close: () -> Unit) {
    var privacy by remember { mutableStateOf(false) }
    var terms by remember { mutableStateOf(false) }
    // Closable (contract "동의 시트 닫기", 2026-10-20): the back arrow leaves without consenting; saving stays blocked until agreed.
    Column(Modifier.fillMaxSize()) {
        DearbyTopBar("동의", close)
        CenteredMessage(Icons.Rounded.Lock, "시작하기 전에 동의가 필요해요", "프로필(이름·연락처·활동 이력)을 저장하려면 아래 두 가지에 동의해 주세요.", Modifier.weight(1f).fillMaxWidth(),
        content = {
            ConsentRow("개인정보 수집·이용에 동의합니다 (필수)", privacy, { privacy = it }, openPrivacy)
            ConsentRow("이용약관에 동의합니다 (필수)", terms, { terms = it }, openTerms)
            error?.let { DearbyFieldError(it) }
        },
            primary = MessageAction(if (consenting) "저장하는 중…" else "동의하고 계속", agree, enabled = privacy && terms, busy = consenting))
    }
}

/** A `ListItem` whose whole row toggles the checkbox; `보기` opens the document. */
@Composable private fun ConsentRow(label: String, checked: Boolean, onChecked: (Boolean) -> Unit, open: () -> Unit) = ListItem(
    { Text(label, Modifier.semantics { heading() }) }, Modifier.toggleable(checked, role = Role.Checkbox, onValueChange = onChecked),
    leadingContent = { Checkbox(checked, null) }, trailingContent = { TextButton(open) { Text("보기") } }, colors = ListItemDefaults.colors(containerColor = Color.Transparent))
