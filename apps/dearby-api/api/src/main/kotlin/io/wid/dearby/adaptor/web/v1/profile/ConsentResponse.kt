package io.wid.dearby.adaptor.web.v1.profile

import io.wid.dearby.domain.profile.Consent
import java.time.OffsetDateTime

// GET /v1/profile의 consent(native-v1.md "개인정보 동의"); 기록이 없으면 null
data class ConsentResponse(val privacyVersion: String, val termsVersion: String, val consentedAt: OffsetDateTime) {
    companion object {
        fun from(c: Consent) = ConsentResponse(c.privacyVersion, c.termsVersion, c.consentedAt)
    }
}
