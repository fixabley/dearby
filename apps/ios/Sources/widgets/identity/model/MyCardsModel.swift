import Foundation
import Observation

/// 내 명함 tab: the signed-in account's published cards. One is selected for viewing, "이 명함 고치기" and "이 명함 지우기".
@MainActor @Observable final class MyCardsModel {
    enum Phase: Equatable { case signedOut, noCard, loading, loaded, failed }
    private(set) var phase = Phase.loading
    private(set) var cards: [PublishedCard] = []
    private(set) var selectedCardID: String?
    let account: AccountViewModel
    /// Where the card QR points (contract "명함 QR 코드"): `<webOrigin>/cards/<id>?activity=<n>&code=<code>`.
    let webOrigin: URL
    /// Applied activities for the QR sheet's activity chip, the one to preselect first (nearest ongoing stage).
    let appliedActivities: @MainActor () -> [ActivityModel]
    init(account: AccountViewModel, webOrigin: URL, appliedActivities: @escaping @MainActor () -> [ActivityModel] = { [] }) {
        self.account = account
        self.webOrigin = webOrigin
        self.appliedActivities = appliedActivities
    }
    var card: PublishedCard? { cards.first { $0.id == selectedCardID } }

    func load() async {
        guard account.session != nil else {
            // Signed out or deleted: drop the previous account's cards.
            cards = []; selectedCardID = nil
            phase = .signedOut
            return
        }
        if cards.isEmpty { phase = .loading }
        let known = Set(cards.map(\.id))
        do {
            cards = try await account.authorized { [client = account.client] in try await client.cards($0) }
        } catch AccountError.unauthorized {
            phase = .signedOut; return
        } catch {
            phase = .failed; return
        }
        guard let newest = cards.last else { phase = .noCard; return }
        // A card that appeared since the last load (just published, from here or 내 프로필) is selected; otherwise the selection stays.
        if !known.isEmpty, let added = cards.last(where: { !known.contains($0.id) }) { selectedCardID = added.id }
        if card == nil { selectedCardID = newest.id }
        phase = .loaded
    }
    func select(_ cardID: String) { selectedCardID = cardID }
    /// A code fetched ahead for the selected card while 내 명함 is on screen (contract "명함 QR 코드" 미리 받기), memory only.
    /// An unused one is not expired; it lapses after its 5 minutes.
    private var prefetched: (cardID: String, code: AccountClient.QRCode)?
    func prefetchQRCode() async {
        guard let cardID = selectedCardID, usablePrefetch(cardID) == nil, let code = await issueQRCode(cardID) else { return }
        prefetched = (cardID, code)
    }
    /// The code the QR screen opens with: the prefetched one when it is this card's and has over 1 minute left, else a new one.
    func openingQRCode(_ cardID: String) async -> AccountClient.QRCode? {
        if let code = usablePrefetch(cardID) { prefetched = nil; return code }
        return await issueQRCode(cardID)
    }
    /// A new code (QR 새로고침, auto-refresh); nil when the server or network refused, so the sheet shows its retry state.
    func issueQRCode(_ cardID: String) async -> AccountClient.QRCode? {
        try? await account.authorized { [client = account.client] in try await client.issueQRCode(cardID, $0) }
    }
    private func usablePrefetch(_ cardID: String) -> AccountClient.QRCode? {
        guard let prefetched, prefetched.cardID == cardID, let expiry = prefetched.code.expiry, expiry.timeIntervalSinceNow > 60 else { return nil }
        return prefetched.code
    }
    /// Expires a shown code without holding up the caller (closing the sheet never waits); the server's 24-hour expiry covers a failure.
    func expireQRCode(_ cardID: String, codeID: String) {
        Task { _ = try? await account.authorized { [client = account.client] in try await client.expireQRCode(cardID, codeID: codeID, $0) } }
    }
    /// 이 명함 지우기: `DELETE /v1/cards/:id`, after which the public link stops working. False when the server or network refused;
    /// the card then stays in the list.
    func delete(_ cardID: String) async -> Bool {
        do { _ = try await account.authorized { [client = account.client] in try await client.deleteCard(cardID, $0) } } catch { return false }
        cards.removeAll { $0.id == cardID }
        if selectedCardID == cardID { selectedCardID = cards.last?.id }
        if cards.isEmpty { phase = .noCard }
        return true
    }
}
