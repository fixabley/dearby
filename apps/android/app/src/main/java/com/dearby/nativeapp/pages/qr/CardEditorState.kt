package com.dearby.nativeapp.pages.qr

data class VisibilityChoiceState(val id: String, val label: String, val kind: String = "", val detail: String = "", val date: String = "")
data class CardEditorState(val contacts: List<VisibilityChoiceState>, val histories: List<VisibilityChoiceState>, val person: String = "", val job: String = "", val introduction: String = "")
data class PublishSelectionState(val name: String, val description: String, val contactIds: Set<String>, val historyIds: Set<String>)
