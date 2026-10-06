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
struct HistoryModel: Identifiable, Equatable {
    let id: String
    var title: String
    var role: String
    var startDate: String
    var endDate: String?
}
struct CardModel: Identifiable, Equatable {
    let id: String
    var name: String
    var profileName: String
    var job: String
    var introduction: String
    var description: String
    var contacts: [ContactModel]
    var histories: [HistoryModel]
}
enum DemoIdentity {
    static let contacts: [ContactModel] = [
        .init(id: "phone", kind: "phone", displayLabel: "전화번호", value: "010-0000-0000"),
        .init(id: "email", kind: "email", displayLabel: "이메일", value: "jimin@example.com"),
        .init(id: "chat", kind: "chat", displayLabel: "카카오톡", value: "https://example.com"),
        .init(id: "instagram", kind: "instagram", displayLabel: "인스타그램", value: "@jimin_example"),
        .init(id: "github", kind: "github", displayLabel: "GitHub", value: "jimin-example"),
        .init(id: "behance", kind: "behance", displayLabel: "Behance", value: "jimin-example")
    ]
    static let histories: [HistoryModel] = [
        .init(id: "conference", title: "Dearby 개발자 컨퍼런스", role: "운영 스태프", startDate: "2026.09"),
        .init(id: "camp", title: "Dearby 메이커 캠프", role: "서비스 기획", startDate: "2026.03", endDate: "08"),
        .init(id: "hackathon", title: "캠퍼스 해커톤", role: "서비스 기획", startDate: "2025.11")
    ]
}
