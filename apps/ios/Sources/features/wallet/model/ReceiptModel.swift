import Foundation

struct ReceiptModel: Codable, Identifiable, Sendable {
    var id: String
    var card: CardModel
    var context: ExchangeContextModel
    var receivedAt: String
    var reciprocal: Bool
}
