package com.dearby.nativeapp.widgets.card.cardContent

data class ContactState(val id: String, val kind: String, val label: String, val value: String)
data class CardHistoryState(val id: String, val title: String, val role: String, val date: String)
data class CardState(
    val id: String, val person: String, val job: String, val title: String,
    val description: String, val introduction: String,
    val contacts: List<ContactState>, val histories: List<CardHistoryState>,
)
