package com.dearby.nativeapp.pages.settings

enum class SettingsPhase { SIGNED_OUT, LOADING, LOADED, FAILED }

/** One provider row of 연결된 로그인: [key] is the API segment (`apple`/`google`), [label] the display name; [linkedAt] null means not linked. */
data class SettingsIdentityState(val key: String, val label: String, val email: String?, val linkedAt: String?) {
    val linked get() = linkedAt != null
}
/** One passkey row: dates are already display strings (`yyyy.M.d`); [lastUsed] null when never used. */
data class SettingsPasskeyState(val id: String, val label: String, val created: String, val lastUsed: String?, val backedUp: Boolean)

/** One blocked person (contract "명함 신고·차단"): [name] is the card name when blocked, [blocked] the day as a display string. */
data class SettingsBlockState(val id: String, val name: String, val blocked: String)

/**
 * 설정 (contract "설정"): [createdAt] is the 가입일 display string, [identities] the Apple·Google rows, [passkeys] this account's passkeys, [blocks] the people blocked from 받은 명함.
 * [busy] while a request runs (buttons disable); [notice] is a one-time result sentence (409 `LAST_SIGN_IN_METHOD`, failures). [version] is the
 * app version·build text for the 정보 section. [adConsent] is the 광고 목적 정보 제공 switch; null hides it (no ads in this build, required consent
 * missing or the read failed).
 */
data class SettingsState(
    val phase: SettingsPhase = SettingsPhase.LOADING, val createdAt: String? = null, val identities: List<SettingsIdentityState> = emptyList(),
    val passkeys: List<SettingsPasskeyState> = emptyList(), val blocks: List<SettingsBlockState> = emptyList(), val busy: Boolean = false, val notice: String? = null, val version: String = "",
    val adConsent: Boolean? = null,
) {
    /** The "last sign-in method" rule: passkeys + linked providers; one means nothing may be removed. */
    val signInMethods get() = passkeys.size + identities.count { it.linked }
    val lastMethod get() = signInMethods <= 1
}
