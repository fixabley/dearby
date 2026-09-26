package com.dearby.nativeapp.widgets.card

import com.dearby.nativeapp.entities.card.model.CardModel

data class CardState(val id: String, val ownerId: String, val person: String, val job: String, val title: String, val description: String, val introduction: String, val contacts: List<String>, val histories: List<String>)
fun CardModel.toState() = CardState(id, ownerId, profileName, job, name, description, introduction, contacts.map { "${it.label.ifBlank { it.kind }} · ${it.value}" }, histories.map { "${it.title} · ${it.role}\n${it.startDate} – ${it.endDate ?: "현재"}\n${it.description}" })
