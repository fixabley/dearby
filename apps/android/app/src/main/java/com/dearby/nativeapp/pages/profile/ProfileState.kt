package com.dearby.nativeapp.pages.profile

import com.dearby.nativeapp.widgets.card.cardContent.ContactState
import com.dearby.nativeapp.widgets.card.cardContent.CardHistoryState

data class ProfileState(val name: String, val job: String, val introduction: String, val contacts: List<ContactState>, val histories: List<CardHistoryState>)
