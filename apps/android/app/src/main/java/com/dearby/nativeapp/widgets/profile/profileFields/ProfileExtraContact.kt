package com.dearby.nativeapp.widgets.profile.profileFields

import com.dearby.nativeapp.entities.account.model.ContactRules

/**
 * 사용자가 골라 추가한 연락처 한 줄의 값. [verifiedValue] (contract "연락처 인증"): the value the server verified for this row, or null; the row shows the 인증됨 mark
 * while [value] still is that username ([verified]) and "값을 바꾸면 인증이 풀려요" once it differs ([unverifying]).
 */
data class ProfileExtraContact(val id: String, val kind: String, val value: String, val verifiedValue: String? = null) {
    val verified: Boolean get() = verifiedValue != null && ContactRules.username(kind, value) == ContactRules.username(kind, verifiedValue)
    val unverifying: Boolean get() = verifiedValue != null && !verified
    /** The fixed text before a username field (`github.com/`); null for 카카오톡 and other kinds. */
    val prefix: String? get() = ContactRules.usernamePrefix(kind)
    /** The row after typing or pasting [text]: username kinds keep the username only, a pasted URL reduced to it (contract "연락처 사용자명 입력", 2026-10-26). */
    fun typed(text: String) = copy(value = ContactRules.sanitizedUsername(kind, text))
}
