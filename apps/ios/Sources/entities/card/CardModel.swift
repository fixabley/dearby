import Foundation

// Wire values belong to the published snapshot, never the private profile.
struct CardModel: Codable, Identifiable, Equatable, Sendable {
    var id: String
    var ownerId: String
    var name: String
    var description: String
    var profileName: String
    var job: String
    var introduction: String
    var contacts: [ContactModel]
    var histories: [HistoryModel]
    var createdAt: String
}
struct CardRequest: Codable, Sendable {
    var name: String
    var description: String
    var contactIds: [String]
    var historyIds: [String]
}
