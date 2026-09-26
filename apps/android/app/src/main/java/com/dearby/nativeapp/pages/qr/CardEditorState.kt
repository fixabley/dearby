package com.dearby.nativeapp.pages.qr

data class VisibilityChoiceState(val id: String, val label: String)
data class CardEditorState(val contacts: List<VisibilityChoiceState>, val histories: List<VisibilityChoiceState>)
data class PublishSelectionState(val name: String, val description: String, val contactIds: Set<String>, val historyIds: Set<String>)
