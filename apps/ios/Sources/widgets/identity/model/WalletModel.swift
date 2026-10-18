import Foundation
import Observation

/// 받은 명함: the account's wallet (`GET /v1/wallet`), an indexed list of saved cards (contract "받은 명함 목록 표시").
/// Signed out, there is nothing to show; the cards live with the account, not on the device.
@MainActor @Observable final class WalletModel {
    enum Phase: Equatable { case signedOut, loading, loaded, failed }
    /// One list row: the card plus the organization chip (the most recent approved history's organization).
    struct Entry: Identifiable, Equatable {
        let card: CardModel
        let organization: String?
        var id: String { card.id }
    }
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
    /// Rows whose name, job, title, body or history titles contain every search term,
    /// grouped by the name's first letter (ㄱ…ㅎ, A…Z, #) and sorted by name inside each group.
    func sections(query: String) -> [(key: String, entries: [Entry])] {
        NameIndex.sections(entries(query: query)) { $0.card.profileName }.map { (key: $0.key, entries: $0.items) }
    }
    private func entries(query: String) -> [Entry] {
        wallet.items.compactMap { receipt in
            let card = receipt.card.cardModel
            guard SearchQuery.matches(query, fields: [card.profileName, card.job, card.name, card.introduction, card.description] + card.histories.map(\.title)) else { return nil }
            return Entry(card: card, organization: receipt.card.latestOrganization)
        }
    }
}

extension PublishedCard {
    /// The saved or own card shown with the identity components.
    var cardModel: CardModel {
        CardModel(id: id, name: name, profileName: profileName, job: job, introduction: introduction ?? "", description: description,
                  contacts: contacts.map { ContactModel(id: $0.id, kind: $0.kind, displayLabel: $0.label, value: $0.value) },
                  histories: histories.map(\.historyModel), organization: latestOrganization)
    }
    /// Organization name of the most recent approved history (start date descending), nil when none names one.
    var latestOrganization: String? {
        histories.filter { $0.approval == "approved" }.sorted { $0.startDate > $1.startDate }
            .lazy.compactMap { $0.organization?.name ?? $0.organizationName }.first { !$0.isEmpty }
    }
}
