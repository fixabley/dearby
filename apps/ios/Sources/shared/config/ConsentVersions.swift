import Foundation

/// The privacy-policy and terms versions this build asks consent for (contract "개인정보 동의"; privacy 2026-10-28 for 연락처 인증·교환 세션·웹 로그인 쿠키, terms 2026-10-22 for 내 활동 서버 저장). They match
/// the effective dates on the web `/privacy` and `/terms` pages; bump them together with the documents. The server's `dearby.consent.privacy-version`
/// follows once testers have this build; until then its `POST /v1/me/consents` answers 400 `CONSENT_VERSION` and the consent screen shows that sentence.
enum ConsentVersions {
    static let privacy = "2026-10-28"
    static let terms = "2026-10-22"
    /// 지원 자격 정보 수집·이용 동의 (contract "추천 설정 퍼널·지원 자격 정보·추천" 2026-10-24): sent as `consentVersion` with the values; a
    /// different stored version asks again. Must match the server's current version.
    static let eligibility = "2026-10-24"
    /// 광고 목적 개인정보 제3자 제공 동의 (contract "광고 목적 개인정보 제3자 제공 동의(선택)" 2026-10-25): the server's `AdConsent.VERSION`; a
    /// different stored version is `agreed=false` and the queue asks again.
    static let adThirdParty = "2026-10-25"
}
