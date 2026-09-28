import Foundation

struct ExchangeContextModel: Codable, Equatable, Sendable {
    var activityId: String?
    var label: String?
    private enum CodingKeys: String, CodingKey { case activityId, label }
    func encode(to encoder: Encoder) throws {
        var values = encoder.container(keyedBy: CodingKeys.self)
        try values.encode(activityId, forKey: .activityId)
        try values.encode(label, forKey: .label)
    }
}
struct GuestSavedCardModel: Codable, Identifiable, Equatable, Sendable {
    var cardId: String
    var context: ExchangeContextModel
    var savedAt: String
    var id: String { cardId }
}
struct ImportResult: Codable, Sendable {
    var cardId: String
    var status: String
    var receiptId: String?
}
