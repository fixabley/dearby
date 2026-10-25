package io.wid.dearby.domain.profile

import java.time.OffsetDateTime

// 광고 목적 개인정보 제3자 제공 동의(선택, native-v1.md "광고 목적 개인정보 제3자 제공 동의(선택)"). 버전은 앱 동의 화면 문구의 시행일
data class AdConsent(val version: String, val consentedAt: OffsetDateTime) {
    companion object {
        const val VERSION = "2026-10-25"
    }
}
