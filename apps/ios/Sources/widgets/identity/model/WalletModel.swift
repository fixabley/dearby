import Foundation
import Observation

/// 받은 명함: the account's wallet (`GET /v1/wallet`), an indexed list of saved cards (contract "받은 명함 목록 표시") that now
/// carry where they were exchanged and when a one-way card is recalled (contract "명함 교환" 2026-10-19).
/// Signed out, there is nothing to show; the cards live with the account, not on the device.
@MainActor @Observable final class WalletModel {
    enum Phase: Equatable { case signedOut, loading, loaded, failed }
    /// One list row: the card, the organization chip (the most recent approved history's organization), the program it was
    /// exchanged at and the recall badge.
    struct Entry: Identifiable, Equatable {
        let receipt: Wallet.Receipt
        let card: CardModel
        let organization: String?
        var id: String { card.id }
        var program: String? { receipt.context.programName }
        /// `맞교환`, `3일 뒤 사라져요` / `오늘 사라져요`, or empty when the server sent no recall time.
        let badge: String
        /// `<프로그램> <활동>에서 받음 · 2026.10.19`; without an activity just `2026.10.19에 받음`.
        var receivedLine: String {
            let day = WalletModel.day(receipt.receivedAt)
            let place = [receipt.context.programName, receipt.context.activityName].compactMap { $0 }.joined(separator: " ")
            return place.isEmpty ? "\(day)에 받음" : "\(place)에서 받음 · \(day)"
        }
    }
    /// A card arriving through `dearby://cards/<cardId>?activity=<activityId>&code=<code>` (the web page's "Dearby 앱에 저장");
    /// the QR code lets a card not yet saved through (contract "명함 QR 코드").
    struct Incoming: Equatable {
        let cardID: String
        let activityID: Int64?
        let code: String?
        init?(url: URL) {
            guard let parts = URLComponents(url: url, resolvingAgainstBaseURL: false), parts.scheme == "dearby", parts.host == "cards" else { return nil }
            let id = parts.path.trimmingCharacters(in: CharacterSet(charactersIn: "/"))
            guard UUID(uuidString: id) != nil else { return nil }
            cardID = id
            activityID = parts.queryItems?.first { $0.name == "activity" }?.value.flatMap { Int64($0) }
            code = parts.queryItems?.first { $0.name == "code" }?.value
        }
    }
    enum Outcome: Equatable { case ownCard, codeExpired, blocked, saveFailed, reciprocateFailed }
    private(set) var phase = Phase.loading
    private(set) var wallet = Wallet(items: [])
    /// An incoming card waiting for sign-in; the page shows the sign-in sheet and then `resumePending()`.
    private(set) var pending: Incoming?
    /// The entry whose detail is open (a tapped row, or a card just saved by QR).
    var opened: Entry?
    /// A one-shot notice for the page; cleared with `clearOutcome()`.
    private(set) var outcome: Outcome?
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
    /// Saves a QR-received card to the account (`PUT /v1/wallet/cards/:id`) and opens it. Signed out, it waits in `pending`.
    func receive(_ incoming: Incoming) async {
        guard account.session != nil else { pending = incoming; return }
        pending = nil
        do {
            let receipt = try await account.authorized { [client = account.client] in
                try await client.receiveCard(incoming.cardID, activityID: incoming.activityID, code: incoming.code, $0)
            }
            await load()
            opened = entry(receipt)
        } catch AccountError.conflict {
            outcome = .ownCard
        } catch AccountError.cardCodeRequired {
            outcome = .codeExpired
        } catch AccountError.blocked {
            outcome = .blocked
        } catch AccountError.unauthorized {
            pending = incoming
        } catch {
            outcome = .saveFailed
        }
    }
    /// After the sign-in sheet closes with a session, the waiting card is saved.
    func resumePending() async {
        guard let pending else { return }
        await receive(pending)
    }
    func dropPending() { pending = nil }
    func clearOutcome() { outcome = nil }
    /// My published cards for `내 명함도 건네기`; nil when they could not be loaded.
    func myCards() async -> [PublishedCard]? {
        try? await account.authorized { [client = account.client] in try await client.cards($0) }
    }
    /// Hands `myCardID` back to the owner of `entry`'s card; the reloaded entry (now reciprocal) replaces the open detail.
    func reciprocate(_ entry: Entry, myCardID: String) async {
        do {
            try await account.authorized { [client = account.client] in try await client.reciprocate(entry.card.id, myCardID: myCardID, $0) }
            await load()
            if let updated = wallet.items.first(where: { $0.card.id == entry.card.id }) { opened = self.entry(updated) }
        } catch {
            outcome = .reciprocateFailed
        }
    }
    /// 신고하기 (contract "명함 신고·차단"); false when the server or network refused.
    func report(_ entry: Entry, reason: CardReportReason, detail: String?) async -> Bool {
        do {
            try await account.authorized { [client = account.client] in try await client.reportCard(entry.card.id, reason: reason, detail: detail, $0) }
            return true
        } catch { return false }
    }
    /// 삭제하고 차단하기: the server drops every card of that owner from my wallet; the detail closes. False when refused.
    func block(_ entry: Entry) async -> Bool {
        do {
            _ = try await account.authorized { [client = account.client] in try await client.block(cardID: entry.card.id, $0) }
        } catch { return false }
        opened = nil
        await load()
        return true
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
            return entry(receipt)
        }
    }
    private func entry(_ receipt: Wallet.Receipt) -> Entry {
        Entry(receipt: receipt, card: receipt.card.cardModel, organization: receipt.card.latestOrganization, badge: Self.badge(receipt))
    }
    /// `맞교환` for a mutual exchange; otherwise the server's recall time as whole Seoul days from today.
    nonisolated static func badge(_ receipt: Wallet.Receipt, now: Date = Date()) -> String {
        if receipt.reciprocal { return "맞교환" }
        guard let expires = receipt.expiresAt.flatMap(ActivityModel.instant) else { return "" }
        let days = seoul.dateComponents([.day], from: seoul.startOfDay(for: now), to: seoul.startOfDay(for: expires)).day ?? 0
        return days <= 0 ? "오늘 사라져요" : "\(days)일 뒤 사라져요"
    }
    /// `2026.10.19` in Seoul; the raw text when the timestamp is unreadable.
    nonisolated static func day(_ raw: String) -> String {
        guard let date = ActivityModel.instant(raw) else { return raw }
        let formatter = DateFormatter()
        formatter.locale = Locale(identifier: "ko_KR")
        formatter.timeZone = seoul.timeZone
        formatter.dateFormat = "yyyy.M.d"
        return formatter.string(from: date)
    }
    private nonisolated static let seoul: Calendar = {
        var calendar = Calendar(identifier: .gregorian)
        calendar.timeZone = TimeZone(identifier: "Asia/Seoul") ?? .current
        return calendar
    }()
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
