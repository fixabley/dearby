struct WalletGroup: Identifiable, Equatable {
    // A card can sit in several groups, so item IDs combine both IDs.
    struct Item: Identifiable, Equatable {
        let id: String
        let card: CardModel
    }
    static let noActivityID = "none"
    let id: String
    let title: String
    let items: [Item]
    let expanded: Bool

    /// Groups one tab's cards by shared activity. `activities` are (id, title) in schedule order.
    /// Empty groups are hidden; while searching every remaining group is shown expanded
    /// without touching `collapsed`.
    static func make(cards: [CardModel], activityIDs: [String: [String]], activities: [(id: String, title: String)],
                     query: String, collapsed: Set<String>) -> [WalletGroup] {
        let titles = Dictionary(uniqueKeysWithValues: activities.map { ($0.id, $0.title) })
        let matches = cards.filter { card in
            SearchQuery.matches(query, fields: [card.profileName, card.job, card.name, card.introduction, card.description]
                + card.histories.map(\.title) + (activityIDs[card.id] ?? []).compactMap { titles[$0] })
        }
        let searching = !query.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty
        return (activities + [(noActivityID, "활동 없음")]).compactMap { group in
            let members = matches.filter { card in
                let ids = activityIDs[card.id] ?? []
                return group.id == noActivityID ? !ids.contains { titles[$0] != nil } : ids.contains(group.id)
            }
            guard !members.isEmpty else { return nil }
            return WalletGroup(id: group.id, title: group.title, items: members.map { Item(id: "\(group.id)/\($0.id)", card: $0) },
                               expanded: searching || !collapsed.contains(group.id))
        }
    }
}
