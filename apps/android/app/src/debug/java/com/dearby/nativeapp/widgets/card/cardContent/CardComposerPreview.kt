package com.dearby.nativeapp.widgets.card.cardContent

import androidx.compose.runtime.*
import androidx.compose.ui.tooling.preview.Preview
import com.dearby.nativeapp.shared.ui.DearbyTheme

/** 미리보기와 계측 캡처가 함께 쓰는 예시. 연락처는 실제가 아닌 example.invalid 값이다. */
@Composable fun CardComposerSample(changed: Boolean = false) {
    var cardTitle by remember { mutableStateOf("") }
    var contacts by remember { mutableStateOf(listOf(ComposerContact("email", "이메일", "jimin@example.invalid", true), ComposerContact("phone", "전화", "010-0000-0000", false))) }
    var histories by remember { mutableStateOf(listOf(ComposerHistory("camp", "Dearby · 메이커 캠프", "운영진 · 2025.03 – 2025.06", true, null), ComposerHistory("x", "직접 적은 조직", "참가자", false, "관리자 승인 뒤 명함에 넣을 수 있어요"))) }
    CardComposer(cardTitle, { cardTitle = it }, "# 김지민\n서비스 기획 · 커뮤니티\n\n- 이메일: jimin@example.invalid", contacts,
        { id, on -> contacts = contacts.map { if (it.id == id) it.copy(isPublic = on) else it } }, histories,
        { id, on -> histories = histories.map { if (it.id == id) it.copy(isPublic = on) else it } }, changed, {}, {})
}

@Preview(name = "명함 제작", widthDp = 390, heightDp = 844) @Composable private fun CardComposerPreview() = DearbyTheme { CardComposerSample() }
@Preview(name = "바뀜", widthDp = 390, heightDp = 844) @Composable private fun CardComposerChangedPreview() = DearbyTheme { CardComposerSample(changed = true) }
