package com.dearby.nativeapp.pages.profile

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.text.style.TextAlign
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
 * [renewal]: the account agreed to an earlier version (the profile's `consent` is not null), so the title says the policy changed (2026-10-26).
 */
@Composable fun ConsentPage(consenting: Boolean, error: String?, openPrivacy: () -> Unit, openTerms: () -> Unit, agree: () -> Unit, close: () -> Unit,
    cancelSignUp: (() -> Unit)? = null, signOut: () -> Unit = {}, renewal: Boolean = false) {
    var privacy by remember { mutableStateOf(false) }
    var terms by remember { mutableStateOf(false) }
    var confirming by remember { mutableStateOf(false) }
    // Closable (contract "동의 시트 닫기", 2026-10-20): the back arrow leaves without consenting; saving stays blocked until agreed.
    Column(Modifier.fillMaxSize()) {
        DearbyTopBar("동의", close)
        CenteredMessage(Icons.Rounded.Lock, if (renewal) "개인정보 처리방침이 바뀌었어요" else "시작하기 전에 동의가 필요해요", "프로필과 내 활동·즐겨찾기 같은 계정 기록을 저장하려면 아래 두 가지에 동의해 주세요.", Modifier.weight(1f).fillMaxWidth(),
        content = {
            // The message keeps to the component's three lines; the rest follows here at the same size, never cut off (2026-10-26 실기기 점검).
            Text("프로필은 이름·연락처·활동 이력과 고른 경우 사진·소개 링크예요. 광고 목적 정보 제공은 여기 포함되지 않고 따로 묻는 선택 항목이에요.",
                color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center, style = MaterialTheme.typography.bodyLarge)
            ConsentRow("개인정보 수집·이용에 동의합니다 (필수)", privacy, { privacy = it }, openPrivacy)
            ConsentRow("이용약관에 동의합니다 (필수)", terms, { terms = it }, openTerms)
            error?.let { DearbyFieldError(it) }
        },
            primary = MessageAction(if (consenting) "저장하는 중…" else "동의하고 계속", agree, enabled = privacy && terms, busy = consenting),
            secondary = MessageAction(if (cancelSignUp != null) "가입 취소" else "로그아웃", { confirming = true }, enabled = !consenting))
    }
    if (confirming) AlertDialog({ confirming = false }, title = { Text(if (cancelSignUp != null) "가입을 취소할까요?" else "로그아웃할까요?") },
        text = cancelSignUp?.let { { Text("가입을 취소하면 방금 만든 계정이 삭제돼요.") } },
        confirmButton = { TextButton({ confirming = false; (cancelSignUp ?: signOut)() }) { Text(if (cancelSignUp != null) "가입 취소" else "로그아웃", color = MaterialTheme.colorScheme.error) } },
        dismissButton = { TextButton({ confirming = false }) { Text(if (cancelSignUp != null) "계속 가입" else "취소") } })
}

/** What an account tab shows while signed in without the current required consent (contract "개인정보 동의" 2026-10-25): its button opens the consent screen. */
@Composable fun ConsentRequiredMessage(ask: () -> Unit, modifier: Modifier = Modifier) =
    CenteredMessage(Icons.Rounded.Lock, "동의가 필요해요", "개인정보 수집·이용과 이용약관에 동의하면 프로필·명함·즐겨찾기 같은 계정 기능을 쓸 수 있어요.", modifier,
        primary = MessageAction("동의하기", ask))

/** A `ListItem` whose whole row toggles the checkbox; `보기` opens the document. */
@Composable private fun ConsentRow(label: String, checked: Boolean, onChecked: (Boolean) -> Unit, open: () -> Unit) = ListItem(
    { Text(label, Modifier.semantics { heading() }) }, Modifier.toggleable(checked, role = Role.Checkbox, onValueChange = onChecked),
    leadingContent = { Checkbox(checked, null) }, trailingContent = { TextButton(open) { Text("보기") } }, colors = ListItemDefaults.colors(containerColor = Color.Transparent))
