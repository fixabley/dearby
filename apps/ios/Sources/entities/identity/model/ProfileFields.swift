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
    private enum CodingKeys: String, CodingKey { case id, title, role, startDate, endDate, description }
    func encode(to encoder: Encoder) throws {
        var values = encoder.container(keyedBy: CodingKeys.self)
        try values.encode(id, forKey: .id)
        try values.encode(title, forKey: .title)
        try values.encode(role, forKey: .role)
        try values.encode(startDate, forKey: .startDate)
        try values.encode(endDate, forKey: .endDate)
        try values.encode(description, forKey: .description)
    }
}
