import Foundation

struct ContactModel: Codable, Identifiable, Equatable, Sendable {
    var id = UUID().uuidString
    var kind = "email"
    var label = ""
    var value = ""
    static let kinds = ["phone", "email", "kakao", "instagram", "github", "behance"]
}
struct HistoryModel: Codable, Identifiable, Equatable, Sendable {
    var id = UUID().uuidString
    var title = ""
    var role = ""
    var startDate = ""
    var endDate: String?
    var description = ""
}
