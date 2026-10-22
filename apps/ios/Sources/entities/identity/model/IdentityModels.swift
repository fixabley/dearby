import Foundation

struct ContactModel: Identifiable, Equatable {
    let id: String
    let kind: String
    var displayLabel: String
    var value: String
    /// Phone and email open the phone or mail app; links open only when they are https.
    var actionURL: URL? {
        switch kind {
        case "phone": URL(string: "tel:" + value.filter { $0.isNumber || $0 == "+" })
        case "email": URL(string: "mailto:" + value)
        default: URL(string: value).flatMap { $0.scheme == "https" ? $0 : nil }
        }
    }
    /// Contract kind → SF Symbol table ("연락처 행은 라벨 대신 왼쪽 아이콘" 2026-10-20); unknown kinds are a plain link.
    static func symbol(kind: String) -> String {
        switch kind {
        case "phone": "phone"
        case "email": "envelope"
        case "kakao": "bubble.left"
        case "instagram": "camera"
        case "github": "chevron.left.forwardslash.chevron.right"
        case "behance": "paintpalette"
        default: "link"
        }
    }
    /// The kind's name (contract vocabulary phone/email/kakao/instagram/github/behance), shown when `displayLabel` is empty.
    var kindName: String {
        switch kind {
        case "phone": "전화"
        case "email": "이메일"
        case "kakao": "카카오톡"
        case "instagram": "Instagram"
        case "github": "GitHub"
        case "behance": "Behance"
        default: "링크"
        }
    }
    /// Row label: the owner's `label`, or the kind name when none was given.
    var rowLabel: String { displayLabel.isEmpty ? kindName : displayLabel }
}
/// A history as shown on cards and profiles: `title` is 조직 → 프로그램 → 활동, `verified` hides the 미확인 badge.
struct HistoryModel: Identifiable, Equatable {
    let id: String
    var title: String
    var role: String
    var startDate: String
    var endDate: String?
    var verified = false
    var description = ""
}
struct CardModel: Identifiable, Equatable {
    let id: String
    var name: String
    var profileName: String
    var job: String
    /// Markdown body (the filled-in card template).
    var introduction: String
    var description: String
    var contacts: [ContactModel]
    var histories: [HistoryModel]
    /// Organization of the most recent approved history, shown under the name (contract "명함 표현").
    var organization: String?
    /// `job` split on ` · ` for the header chips.
    var jobs: [String] { job.components(separatedBy: " · ").map { $0.trimmingCharacters(in: .whitespaces) }.filter { !$0.isEmpty } }
}
extension CardModel {
    /// Sample values for a loading card (`dearbySkeleton`, contract "내용 로딩은 스켈레톤" 2026-10-22); never shown unredacted.
    static let placeholder = CardModel(
        id: "", name: "내 명함", profileName: "이름", job: "직무 · 직무", introduction: "자기소개 한두 줄이 여기에 보여요.\n\n연락처와 이력이 이어서 보여요.",
        description: "", contacts: [ContactModel(id: "p", kind: "email", displayLabel: "", value: "name@example.com")],
        histories: [HistoryModel(id: "h", title: "조직 · 프로그램 · 활동", role: "역할", startDate: "2026.01", endDate: "진행 중")],
        organization: "조직 이름")
}
