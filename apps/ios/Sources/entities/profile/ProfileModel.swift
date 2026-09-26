import Foundation

struct ProfileModel: Codable, Equatable, Sendable {
    var id = UUID().uuidString
    var name = ""
    var job = ""
    var introduction = ""
    var contacts: [ContactModel] = []
    var histories: [HistoryModel] = []
    var updatedAt = ""
}
