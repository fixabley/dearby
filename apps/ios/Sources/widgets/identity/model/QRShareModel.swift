import Foundation
import Observation

/// The QR tab's real share: the signed-in account's card, shared with optional activities as `<web>/s/<id>`.
/// One share is made per card and activity choice in this app session, so re-entering the tab reuses it.
@MainActor @Observable final class QRShareModel {
    enum Phase: Equatable { case signedOut, noCard, loading, ready(URL), failed(String) }
    private(set) var phase = Phase.loading
    private(set) var cards: [PublishedCard] = []
    private(set) var selectedCardID: String?
    private(set) var activityIDs: Set<String> = []
    private var links: [String: URL] = [:]
    let account: AccountViewModel
    private let link: (String) -> URL
    init(account: AccountViewModel, link: @escaping (String) -> URL) {
        self.account = account
        self.link = link
    }
    var card: PublishedCard? { cards.first { $0.id == selectedCardID } }

    func load() async {
        guard account.session != nil else { phase = .signedOut; return }
        phase = .loading
        do {
            cards = try await account.authorized { [client = account.client] in try await client.cards($0) }
        } catch AccountError.unauthorized {
            phase = .signedOut; return
        } catch {
            phase = .failed("명함을 불러오지 못했어요. 다시 시도해 주세요."); return
        }
        guard let newest = cards.last else { phase = .noCard; return }
        if card == nil { selectedCardID = newest.id }
        await share()
    }
    func select(_ cardID: String) async {
        selectedCardID = cardID
        await share()
    }
    func choose(_ ids: Set<String>) async {
        activityIDs = ids
        await share()
    }
    private func share() async {
        guard let card else { return }
        let ids = activityIDs.sorted(), key = ([card.id] + ids).joined(separator: " ")
        if let url = links[key] { phase = .ready(url); return }
        phase = .loading
        let result: Phase
        do {
            let share = try await account.authorized { [client = account.client] in try await client.share(card.id, activityIds: ids, $0) }
            links[key] = link(share.id)
            result = .ready(link(share.id))
        } catch AccountError.unauthorized {
            result = .signedOut
        } catch AccountError.invalidInput {
            result = .failed("고른 활동 중 지금 모집 정보에 없는 활동이 있어요. 활동 선택을 바꿔 주세요.")
        } catch {
            result = .failed("QR을 만들지 못했어요. 다시 시도해 주세요.")
        }
        // A later card or activity choice may have started its own share meanwhile; only the current one shows.
        if key == ([selectedCardID ?? ""] + activityIDs.sorted()).joined(separator: " ") { phase = result }
    }
}
