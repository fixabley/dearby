package com.dearby.nativeapp.features.account

import com.dearby.nativeapp.entities.profile.model.*
import com.dearby.nativeapp.entities.card.model.*

data class AccountState(
    val ready: Boolean = false,
    val busy: Boolean = false,
    val loggedIn: Boolean = false,
    val profile: ProfileModel = ProfileModel(),
    val cards: List<CardModel> = emptyList(),
    val wallet: List<ReceiptModel> = emptyList(),
    val guests: List<GuestSavedCardModel> = emptyList(),
    val guestCards: Map<String, CardModel> = emptyMap(),
    val selectedCardId: String? = null,
    val importVisible: Boolean = false,
    val challengeId: String? = null,
    val challengeExpires: String? = null,
    val message: String? = null,
)
