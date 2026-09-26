import Foundation

struct ExchangeContextModel: Codable, Equatable, Sendable {
    var activityId: String?
    var label: String?
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
