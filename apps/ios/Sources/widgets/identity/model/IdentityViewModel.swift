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
    var selectedCardID: String? = "mine-0"
    func save(_ card: CardModel) {
        savedCardIDs.insert(card.id)
        if !received.contains(where: { $0.id == card.id }) { received.append(card) }
    }
    func send(to card: CardModel) {
        save(card)
        reciprocalIDs.insert(card.id)
    }
}
