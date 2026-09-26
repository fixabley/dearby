import Foundation

struct ExchangeRequest: Codable, Sendable, Equatable {
    let cardId: String
    let recipientProfileId: String
    let context: ExchangeContextModel
    let requestId: String
}
@MainActor final class ExchangeState {
    private let store: LocalStore
    init(store: LocalStore) { self.store = store }
    func request(accountID: String, cardID: String, recipientID: String,
                 context: ExchangeContextModel) throws -> ExchangeRequest {
        let key = "pendingExchange.\(accountID)"
        let old = try store.read(ExchangeRequest?.self, key: key) ?? nil
        if let old, old.cardId == cardID, old.recipientProfileId == recipientID, old.context == context { return old }
        let request = ExchangeRequest(cardId: cardID, recipientProfileId: recipientID,
            context: context, requestId: UUID().uuidString)
        try store.write(request, key: key)
        return request
    }
    func complete(accountID: String) throws {
        try store.write(Optional<ExchangeRequest>.none, key: "pendingExchange.\(accountID)")
    }
}
