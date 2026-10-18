import Foundation
import Observation

/// 받은 명함: the account's wallet (`GET /v1/wallet`), a plain list of saved cards.
/// Signed out, there is nothing to show; the cards live with the account, not on the device.
@MainActor @Observable final class WalletModel {
    enum Phase: Equatable { case signedOut, loading, loaded, failed }
    private(set) var phase = Phase.loading
    private(set) var wallet = Wallet(items: [])
    let account: AccountViewModel
    init(account: AccountViewModel) { self.account = account }

    func load() async {
        guard account.session != nil else {
            wallet = Wallet(items: [])  // signed out or deleted: drop the previous account's cards
            phase = .signedOut
            return
        }
        if wallet.items.isEmpty { phase = .loading }
        do {
            wallet = try await account.authorized { [client = account.client] in try await client.wallet($0) }
            phase = .loaded
        } catch AccountError.unauthorized {
            phase = .signedOut
        } catch {
            phase = .failed
        }
    }
    /// Cards whose name, job, title, body or history titles contain every search term.
    func cards(query: String) -> [CardModel] {
        wallet.items.map(\.card.cardModel).filter { card in
            SearchQuery.matches(query, fields: [card.profileName, card.job, card.name, card.introduction, card.description] + card.histories.map(\.title))
        }
    }
}

extension PublishedCard {
    /// The saved or own card shown with the identity components.
    var cardModel: CardModel {
        CardModel(id: id, name: name, profileName: profileName, job: job, introduction: introduction ?? "", description: description,
                  contacts: contacts.map { ContactModel(id: $0.id, kind: $0.kind, displayLabel: $0.label, value: $0.value) },
                  histories: histories.map(\.historyModel))
    }
}
