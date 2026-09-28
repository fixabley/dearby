package com.dearby.nativeapp.pages.profile

data class ProfileState(val name: String, val job: String, val introduction: String, val contacts: List<ContactState>, val histories: List<HistoryState>)
data class ContactState(val id: String, val kind: String, val label: String, val value: String)
data class HistoryState(val id: String, val title: String, val role: String, val startDate: String, val endDate: String? = null, val description: String = "")

fun contactKindLabel(kind: String) = when (kind) { "phone" -> "전화"; "email" -> "이메일"; "kakao" -> "카카오톡"; "instagram" -> "인스타그램"; "github" -> "GitHub"; "behance" -> "Behance"; else -> "연락처" }
