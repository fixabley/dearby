package com.dearby.nativeapp.entities.profile.model

import kotlinx.serialization.Serializable

@Serializable data class ContactModel(val id: String, val kind: String, val label: String, val value: String)
@Serializable data class HistoryModel(val id: String, val title: String, val role: String, val startDate: String, val endDate: String? = null, val description: String = "")
@Serializable data class ProfileModel(val id: String = "local", val name: String = "", val job: String = "", val introduction: String = "", val contacts: List<ContactModel> = emptyList(), val histories: List<HistoryModel> = emptyList(), val updatedAt: String = "")
@Serializable data class ProfileUpdate(val name: String, val job: String, val introduction: String, val contacts: List<ContactModel>, val histories: List<HistoryModel>)
fun ProfileModel.update() = ProfileUpdate(name, job, introduction, contacts, histories)
