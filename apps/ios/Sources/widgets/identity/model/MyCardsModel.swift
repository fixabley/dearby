import Foundation
import Observation

/// 내 명함 tab: the signed-in account's published cards. One is selected for viewing and "이 명함 고치기".
@MainActor @Observable final class MyCardsModel {
    enum Phase: Equatable { case signedOut, noCard, loading, loaded, failed }
    private(set) var phase = Phase.loading
    private(set) var cards: [PublishedCard] = []
    private(set) var selectedCardID: String?
    let account: AccountViewModel
    init(account: AccountViewModel) { self.account = account }
    var card: PublishedCard? { cards.first { $0.id == selectedCardID } }

    func load() async {
        guard account.session != nil else {
            // Signed out or deleted: drop the previous account's cards.
            cards = []; selectedCardID = nil
            phase = .signedOut
            return
        }
        if cards.isEmpty { phase = .loading }
        do {
            cards = try await account.authorized { [client = account.client] in try await client.cards($0) }
        } catch AccountError.unauthorized {
            phase = .signedOut; return
        } catch {
            phase = .failed; return
        }
        guard let newest = cards.last else { phase = .noCard; return }
        if card == nil { selectedCardID = newest.id }
        phase = .loaded
    }
    func select(_ cardID: String) { selectedCardID = cardID }
}
