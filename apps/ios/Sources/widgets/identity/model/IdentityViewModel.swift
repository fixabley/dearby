import Observation

@MainActor @Observable final class IdentityViewModel {
    var signedIn = false
    var profileName = "김지민"
    var introduction = "사람을 연결하는 경험을 만듭니다."
    var job = "서비스 기획 · 커뮤니티"
    var contacts = DemoIdentity.contacts
    var histories = DemoIdentity.histories
    var cards = DemoIdentity.myCards
    var received = DemoIdentity.received
    var reciprocalIDs: Set<String> = ["received-3", "received-4"]
    var savedCardIDs: Set<String> = []
    var presetContactIDs: Set<String>?
    var presetHistoryIDs: Set<String>?
    var selectedCardID: String? = "mine-0"
    func save(_ card: CardModel) {
        savedCardIDs.insert(card.id)
        if !received.contains(where: { $0.id == card.id }) { received.append(card) }
    }
    func send(to card: CardModel) {
        save(card)
        reciprocalIDs.insert(card.id)
    }
    func makeCard(name: String, editingID: String? = nil, contactIDs: Set<String>, historyIDs: Set<String>) -> CardModel {
        let card = CardModel(id: editingID ?? "mine-\(cards.count)", name: name, profileName: profileName, job: job,
            introduction: introduction, description: "새로운 인연에게 나를 소개해요.",
            contacts: contacts.filter { contactIDs.contains($0.id) }, histories: histories.filter { historyIDs.contains($0.id) })
        if let index = cards.firstIndex(where: { $0.id == card.id }) { cards[index] = card } else { cards.append(card) }
        selectedCardID = card.id
        return card
    }
}
