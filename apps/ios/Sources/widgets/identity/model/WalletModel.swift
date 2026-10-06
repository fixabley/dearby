import Foundation
import Observation

/// 받은 명함: the account's wallet (`GET /v1/wallet`, contract #141), grouped by the activities its shares carried.
/// Signed out, there is nothing to show; the cards live with the account, not on the device.
@MainActor @Observable final class WalletModel {
    enum Phase: Equatable { case signedOut, loading, loaded, failed }
    private(set) var phase = Phase.loading
    private(set) var wallet = Wallet(items: [], shares: [])
    let account: AccountViewModel
    init(account: AccountViewModel) { self.account = account }

    func load() async {
        guard account.session != nil else { phase = .signedOut; return }
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
    var cards: [CardModel] { wallet.items.map(\.card.cardModel) }
    /// Same rule as the web `/saved`: a card sits under every activity its shares carried; groups follow the order
    /// activities were first saved, and cards without any go to the last "활동 없음" group.
    func groups(query: String, collapsed: Set<String>) -> [WalletGroup] {
        let ordered = wallet.shares.sorted { $0.savedAt < $1.savedAt }
        var activities: [(id: String, title: String)] = []
        var byCard: [String: [String]] = [:]
        for share in ordered {
            for activity in share.activities {
                if !activities.contains(where: { $0.id == activity.id }) { activities.append((activity.id, activity.title)) }
                if !(byCard[share.cardId] ?? []).contains(activity.id) { byCard[share.cardId, default: []].append(activity.id) }
            }
        }
        return WalletGroup.make(cards: cards, activityIDs: byCard, activities: activities, query: query, collapsed: collapsed)
    }
}

extension PublishedCard {
    /// The shared card shown with the identity components.
    var cardModel: CardModel {
        CardModel(id: id, name: name, profileName: profileName, job: job, introduction: introduction ?? "", description: description,
                  contacts: contacts.map { ContactModel(id: $0.id, kind: $0.kind, displayLabel: $0.label, value: $0.value) },
                  histories: histories.map { HistoryModel(id: $0.id, title: $0.title, role: $0.role, startDate: $0.startDate, endDate: $0.endDate) })
    }
}
