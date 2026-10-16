package io.wid.dearby.application.profile

import java.util.*

// PUT /v1/profile의 연락처. kind는 소문자(phone·email·kakao·instagram·github·behance)
data class ContactInput(val id: UUID, val kind: String, val label: String, val value: String)
