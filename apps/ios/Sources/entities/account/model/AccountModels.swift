import Foundation

/// Owner session from `POST /v1/auth/sessions`; stored only in the Keychain.
struct AccountSession: Codable, Equatable, Sendable {
    let sessionToken: String
    let profileId: String
}
/// Profile contact. IDs are client-generated UUIDs; the server keeps them as given.
struct AccountContact: Codable, Equatable, Identifiable, Sendable {
    let id: String
    var kind: String
    var label: String
    var value: String
}
struct AccountHistory: Codable, Equatable, Identifiable, Sendable {
    let id: String
    var title: String
    var role: String
    var startDate: String
    var endDate: String?
    var description: String
    private enum CodingKeys: String, CodingKey { case id, title, role, startDate, endDate, description }
    // The API requires `endDate` to be present, as null when ongoing.
    func encode(to encoder: Encoder) throws {
        var container = encoder.container(keyedBy: CodingKeys.self)
        try container.encode(id, forKey: .id)
        try container.encode(title, forKey: .title)
        try container.encode(role, forKey: .role)
        try container.encode(startDate, forKey: .startDate)
        try container.encode(endDate, forKey: .endDate)
        try container.encode(description, forKey: .description)
    }
}
/// `PUT /v1/profile` replaces the whole profile; `GET` returns the same fields plus id and updatedAt.
struct AccountProfile: Codable, Equatable, Sendable {
    var name: String
    var job: String
    var introduction: String
    var contacts: [AccountContact]
    var histories: [AccountHistory]
}
/// A catalog activity copied into a share when it was made; never follows later catalog edits.
struct ShareActivity: Codable, Equatable, Sendable {
    let id: String
    let title: String
}
/// A recorded share of one card (`POST /v1/cards/:id/shares`); its ID is the public `/s/<id>` link.
struct CardShare: Decodable, Equatable, Sendable {
    let id: String
    let cardId: String
    let activities: [ShareActivity]
    let createdAt: String
}
/// `GET /v1/shares/:id`: a share and the public card it points at.
struct ReceivedShare: Decodable, Equatable, Sendable {
    let share: CardShare
    let card: PublishedCard
}
/// A published card snapshot from `POST /v1/cards`.
struct PublishedCard: Decodable, Equatable, Identifiable, Sendable {
    let id: String
    let name: String
    let description: String
    let profileName: String
    let job: String
    /// Present on public reads (`GET /v1/shares/:id`, `GET /v1/cards/:id`).
    var introduction: String? = nil
    let contacts: [AccountContact]
    let histories: [AccountHistory]
    let createdAt: String
}
