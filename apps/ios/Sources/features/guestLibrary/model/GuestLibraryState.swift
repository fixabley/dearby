import Foundation
import Observation

@MainActor @Observable final class GuestLibraryState {
    private let store: LocalStore
    private(set) var items: [GuestSavedCardModel]
    init(store: LocalStore) throws {
        self.store = store
        items = try store.read([GuestSavedCardModel].self, key: "guests") ?? []
    }
    func save(cardID: String, context: ExchangeContextModel) throws {
        guard UUID(uuidString: cardID) != nil else { throw APIError.invalidResponse }
        guard !items.contains(where: { $0.cardId == cardID }) else { return }
        let updated = items + [GuestSavedCardModel(cardId: cardID, context: context,
            savedAt: ISO8601DateFormatter().string(from: Date()))]
        try store.write(updated, key: "guests")
        items = updated
    }
    func applyImport(_ results: [ImportResult], selected: Set<String>) throws {
        let successful = Set(results.filter {
            selected.contains($0.cardId) && ["imported", "alreadySaved"].contains($0.status) && UUID(uuidString: $0.receiptId ?? "") != nil
        }.map(\.cardId))
        let remaining = items.filter { !successful.contains($0.cardId) }
        try store.write(remaining, key: "guests")
        items = remaining
    }
}
