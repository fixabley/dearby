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
        let key = try storageKey(accountID: accountID, cardID: cardID, recipientID: recipientID, context: context)
        let old = try store.read(ExchangeRequest?.self, key: key) ?? nil
        if let old, old.cardId == cardID, old.recipientProfileId == recipientID, old.context == context { return old }
        let request = ExchangeRequest(cardId: cardID, recipientProfileId: recipientID,
            context: context, requestId: UUID().uuidString)
        try store.write(request, key: key)
        return request
    }
    func complete(accountID: String, request: ExchangeRequest) throws {
        let key = try storageKey(accountID: accountID, cardID: request.cardId,
            recipientID: request.recipientProfileId, context: request.context)
        try store.write(Optional<ExchangeRequest>.none, key: key)
    }
    private func storageKey(accountID: String, cardID: String, recipientID: String,
                            context: ExchangeContextModel) throws -> String {
        let identity = [accountID, cardID, recipientID, context.activityId ?? "", context.label ?? ""]
        return "pendingExchange." + (try JSONEncoder().encode(identity)).base64EncodedString()
    }
}
