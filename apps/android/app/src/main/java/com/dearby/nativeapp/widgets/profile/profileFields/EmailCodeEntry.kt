package com.dearby.nativeapp.widgets.profile.profileFields

/**
 * 이메일 인증번호 입력 (contract "연락처 인증"): the 6 digits typed so far, the server's sentence for a wrong code (tries left) under the field, and when `다시 받기`
 * wakes up ([resendAt], epoch millis: one minute after the last mail, also after a 429). The challenge id stays with the screen state.
 */
data class EmailCodeEntry(val code: String = "", val error: String? = null, val resendAt: Long = 0L) {
    val complete: Boolean get() = code.length == LENGTH
    companion object { const val LENGTH = 6 }
}
