package com.dearby.nativeapp.pages.profile

import com.dearby.nativeapp.widgets.card.cardContent.CardHistoryState
import com.dearby.nativeapp.widgets.card.cardContent.ContactState
import com.dearby.nativeapp.widgets.profile.profileFields.ProfileExtraContact
import java.time.LocalDate

/** The saved profile as shown on 내 프로필. */
data class ProfileState(val name: String, val job: String, val introduction: String, val contacts: List<ContactState>, val histories: List<CardHistoryState>)
enum class ProfilePhase { SIGNED_OUT, LOADING, LOADED, FAILED }
data class ProfileViewState(val phase: ProfilePhase = ProfilePhase.LOADING, val profile: ProfileState? = null)

/** The edit form (contract #149): required phone and email, added contacts, and history periods as dates. */
data class HistoryFormState(val id: String, val title: String, val role: String, val start: LocalDate?, val end: LocalDate?, val ongoing: Boolean, val description: String)
data class ProfileFormState(
    val name: String, val job: String, val introduction: String, val phone: String, val email: String,
    val extras: List<ProfileExtraContact>, val histories: List<HistoryFormState>,
)
/** Messages shown once the user tries to save; [histories] maps history IDs to their message. */
data class ProfileErrors(val name: String? = null, val phone: String? = null, val email: String? = null, val histories: Map<String, String> = emptyMap())
