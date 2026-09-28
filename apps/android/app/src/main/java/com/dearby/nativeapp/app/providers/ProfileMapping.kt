package com.dearby.nativeapp.app.providers

import com.dearby.nativeapp.entities.profile.model.ContactModel
import com.dearby.nativeapp.entities.profile.model.HistoryModel
import com.dearby.nativeapp.entities.profile.model.ProfileModel
import com.dearby.nativeapp.entities.card.model.CardSelectionModel
import com.dearby.nativeapp.pages.profile.contactKindLabel
import com.dearby.nativeapp.pages.profile.ContactState
import com.dearby.nativeapp.pages.profile.HistoryState
import com.dearby.nativeapp.pages.profile.ProfileState
import com.dearby.nativeapp.pages.qr.CardEditorState
import com.dearby.nativeapp.pages.qr.PublishSelectionState
import com.dearby.nativeapp.pages.qr.VisibilityChoiceState

fun ProfileModel.editorState() = ProfileState(name, job, introduction, contacts.map { ContactState(it.id, it.kind, it.label, it.value) }, histories.map { HistoryState(it.id, it.title, it.role, it.startDate, it.endDate, it.description) })
fun ProfileState.applyTo(original: ProfileModel) = original.copy(name = name, job = job, introduction = introduction, contacts = contacts.map { ContactModel(it.id, it.kind, it.label, it.value) }, histories = histories.map { HistoryModel(it.id, it.title, it.role, it.startDate, it.endDate, it.description) })
fun ProfileModel.visibilityState() = CardEditorState(contacts.map { VisibilityChoiceState(it.id, it.label.ifBlank { contactKindLabel(it.kind) }, it.kind, it.value) }, histories.map { VisibilityChoiceState(it.id, it.title, detail = it.role, date = "${it.startDate} – ${it.endDate ?: "현재"}") }, name, job, introduction)
fun PublishSelectionState.selectionModel() = CardSelectionModel(name, description, contactIds, historyIds)
