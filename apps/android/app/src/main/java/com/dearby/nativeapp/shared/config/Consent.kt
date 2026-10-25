package com.dearby.nativeapp.shared.config

/**
 * 개인정보 동의 (contract "개인정보 동의", 2026-10-19; versions 2026-10-22 with "내 활동 서버 저장"): the document versions the app agrees to. They match the 시행일 of the web
 * `/privacy` and `/terms` pages; the server rejects any other value (400 CONSENT_VERSION) and asks again when its versions move on.
 */
object ConsentVersions {
    const val PRIVACY = "2026-10-22"
    const val TERMS = "2026-10-22"
    /** 지원 자격 정보 수집·이용 동의 (contract "추천 설정 퍼널·지원 자격 정보·추천", 2026-10-24). The contract names no version string yet; the decision date until the server publishes one. */
    const val ELIGIBILITY = "2026-10-24"
    /** 광고 목적 개인정보 제3자 제공 동의(선택) (contract, 2026-10-25): the server's `AdConsent.VERSION`. */
    const val AD = "2026-10-25"
    fun privacyUrl(webOrigin: String) = "$webOrigin/privacy"
    fun termsUrl(webOrigin: String) = "$webOrigin/terms"
}
