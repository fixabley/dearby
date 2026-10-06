package com.dearby.nativeapp.widgets.card.cardContent

import androidx.compose.runtime.*
import androidx.compose.ui.tooling.preview.Preview
import com.dearby.nativeapp.shared.ui.DearbyTheme

/** 미리보기와 계측 캡처가 함께 쓰는 예시. 연락처는 실제가 아닌 example.invalid 값이다. */
@Composable fun CardComposerSample(initialName: String = "김지민") {
    var name by remember { mutableStateOf(initialName) }
    var job by remember { mutableStateOf("서비스 기획 · 커뮤니티") }
    var introduction by remember { mutableStateOf("") }
    var contacts by remember { mutableStateOf(listOf(CardComposerContact("email", "email", "이메일", "jimin@example.invalid", true), CardComposerContact("phone", "phone", "전화", "", false))) }
    var histories by remember { mutableStateOf(listOf(CardComposerHistory("camp", "Dearby 메이커 캠프", "2025 · 운영진", true))) }
    CardComposer(name, job, introduction, contacts, histories, { name = it }, { job = it }, { introduction = it },
        { c -> contacts = contacts.map { if (it.id == c.id) c else it } }, { h -> histories = histories.map { if (it.id == h.id) h else it } },
        {}, requiresLogin = true)
}

@Preview(name = "명함 제작", widthDp = 390, heightDp = 844) @Composable private fun CardComposerPreview() = DearbyTheme { CardComposerSample() }
@Preview(name = "이름 없음", widthDp = 390, heightDp = 844) @Composable private fun CardComposerEmptyPreview() = DearbyTheme { CardComposerSample("") }
