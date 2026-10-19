package io.wid.dearby.domain.profile

import java.time.OffsetDateTime

// 사용자가 동의한 문서 버전(native-v1.md "개인정보 동의"). 버전은 문서 시행일 문자열이다
data class Consent(val privacyVersion: String, val termsVersion: String, val consentedAt: OffsetDateTime)

// 지금 서버가 요구하는 문서 버전
data class ConsentVersions(val privacy: String, val terms: String) {
    fun satisfiedBy(consent: Consent?) = consent != null && consent.privacyVersion == privacy && consent.termsVersion == terms
}

// GET /v1/profile에 싣는 동의 상태. required면 PUT /v1/profile 등 프로필 쓰기는 403 CONSENT_REQUIRED
data class ConsentStatus(val consent: Consent?, val required: Boolean)
