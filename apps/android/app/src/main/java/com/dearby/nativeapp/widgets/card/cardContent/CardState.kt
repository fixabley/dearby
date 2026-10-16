package com.dearby.nativeapp.widgets.card.cardContent

data class ContactState(val id: String, val kind: String, val label: String, val value: String)
/** [title] is 조직 · 프로그램 · 활동; [verified] hides the 미확인 badge (contract "이력"). */
data class CardHistoryState(val id: String, val title: String, val role: String, val date: String, val verified: Boolean = false)
data class CardState(
    val id: String, val person: String, val job: String, val title: String,
    /** [introduction] is the card body: the filled-in markdown template. */
    val description: String, val introduction: String,
    val contacts: List<ContactState>, val histories: List<CardHistoryState>,
)
