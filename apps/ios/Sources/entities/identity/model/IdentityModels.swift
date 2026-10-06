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
