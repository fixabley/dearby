package com.dearby.nativeapp.widgets.card.cardContent

import com.dearby.nativeapp.entities.account.model.ContactRules

data class ContactState(val id: String, val kind: String, val label: String, val value: String) {
    /** 누르면 여는 주소(2026-10-26 "연락처 아이콘·바로가기", `ContactRules.link`); 있으면 행에 외부 링크 아이콘을 둔다. `openContact`가 같은 값을 쓴다. */
    val target: String? get() = ContactRules.link(kind, value)
    /** 주소가 아닌 카카오톡 ID: 누르면 복사한다(행에 복사 아이콘). */
    val copyText: String? get() = ContactRules.copyText(kind, value)
}
/** [title] is 조직 · 프로그램 · 활동; [verified] picks the 확인됨/미확인 badge; [description] is the optional one-line note (contract "이력"). */
data class CardHistoryState(val id: String, val title: String, val role: String, val date: String, val verified: Boolean = false, val description: String = "")
data class CardState(
    /** [title]·[description] are the card's own name and note: the card face does not draw them (contract "명함 표현"), but the wallet search (`filterWallet`) matches on them. */
    val id: String, val person: String, val job: String, val title: String,
    /** [introduction] is the card body (markdown): the profile introduction at publish. */
    val description: String, val introduction: String,
    val contacts: List<ContactState>, val histories: List<CardHistoryState>,
    /** 명함 머리의 소속·받은 명함 목록 칩: 가장 최근 승인 이력의 조직 이름(없으면 null). */
    val organization: String? = null,
    /** 명함 사진(contract "프로필 사진"): API 경로, 없으면 null. 머리 아바타가 원형으로 그린다. */
    val photoUrl: String? = null,
)
