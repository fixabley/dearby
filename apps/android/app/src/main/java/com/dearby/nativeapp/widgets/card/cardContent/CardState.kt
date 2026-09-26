package com.dearby.nativeapp.widgets.card.cardContent

import com.dearby.nativeapp.entities.card.model.CardModel
import com.dearby.nativeapp.features.contact.ContactActionState
import com.dearby.nativeapp.features.contact.contactAction

data class CardHistoryState(val id: String, val title: String, val role: String, val startDate: String, val endDate: String?, val description: String)
data class CardState(val id: String, val ownerId: String, val person: String, val job: String, val title: String, val description: String, val introduction: String, val contacts: List<ContactActionState>, val histories: List<CardHistoryState>)
fun CardModel.toState() = CardState(id, ownerId, profileName, job, name, description, introduction,
    contacts.map { contactAction(it.id, it.kind, it.label, it.value) },
    histories.map { CardHistoryState(it.id, it.title, it.role, it.startDate, it.endDate, it.description) })
