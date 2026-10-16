import Foundation

struct ContactModel: Identifiable, Equatable {
    let id: String
    let kind: String
    var displayLabel: String
    var value: String
    var symbol: String {
        switch kind {
        case "phone": "phone"
        case "email": "envelope"
        case "chat": "bubble"
        case "instagram": "camera"
        default: "link"
        }
    }
}
/// A history as shown on cards and profiles: `title` is 조직 → 프로그램 → 활동, `verified` hides the 미확인 badge.
struct HistoryModel: Identifiable, Equatable {
    let id: String
    var title: String
    var role: String
    var startDate: String
    var endDate: String?
    var verified = false
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
}
