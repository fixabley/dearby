import Foundation

/// The privacy-policy and terms versions this build asks consent for (contract "개인정보 동의"; 2026-10-22 for 내 활동 서버 저장). They match
/// the effective dates on the web `/privacy` and `/terms` pages; bump them together with the documents.
enum ConsentVersions {
    static let privacy = "2026-10-22"
    static let terms = "2026-10-22"
    /// 지원 자격 정보 수집·이용 동의 (contract "추천 설정 퍼널·지원 자격 정보·추천" 2026-10-24): sent as `consentVersion` with the values; a
    /// different stored version asks again. Must match the server's current version.
    static let eligibility = "2026-10-24"
}
