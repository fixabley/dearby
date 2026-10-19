import Foundation

/// The privacy-policy and terms versions this build asks consent for (contract "개인정보 동의", 2026-10-19). They match
/// the effective dates on the web `/privacy` and `/terms` pages; bump them together with the documents.
enum ConsentVersions {
    static let privacy = "2026-10-19"
    static let terms = "2026-10-19"
}
