import Foundation
import Observation

@MainActor @Observable final class AppState {
    let api: APIClient
    let loginState: LoginState
    let catalogState: CatalogState
    var activeTab = 0
    var incomingURL: URL?
    func receiveURL(_ url: URL) {
        do { _ = try CardLink(parsing: url); incomingURL = url; activeTab = 2 } catch { message = error.localizedDescription }
    }
    var recipientID: String?
    var recipient: CardModel? { receipts.first { $0.card.ownerId == recipientID }?.card }
    static func open() throws -> AppState { try AppState(store: LocalStore()) }
    func resolveCard(_ id: String) async throws -> CardModel {
        let card: CardModel = try await api.request("GET", "cards/\(id)")
        guard UUID(uuidString: card.id) == UUID(uuidString: id) else { throw APIError.invalidResponse }
        return card
    }
    private let vault: SessionVault
    let profileState: ProfileState
    let guestLibrary: GuestLibraryState
    private let exchangeState: ExchangeState
    var profile: ProfileModel { profileState.value }
    var guests: [GuestSavedCardModel] { guestLibrary.items }
    var cards: [CardModel] = []
    var receipts: [ReceiptModel] = []
    var session: SessionModel?
    var message: String?
    var busy = false
    var showImport = false
    var selectedCardID: String?
    init(store: LocalStore, api: APIClient = .configured) throws {
        self.api = api
        loginState = LoginState(api: api)
        catalogState = try CatalogState(store: store, api: api)
        vault = SessionVault(namespace: api.baseURL?.absoluteString ?? "unconfigured")
        profileState = try ProfileState(store: store)
        guestLibrary = try GuestLibraryState(store: store)
        exchangeState = ExchangeState(store: store)
        session = try vault.load()
        try profileState.selectAccount(session?.profileId)
    }
    func saveProfile(_ draft: ProfileModel) throws { try profileState.save(draft) }
    func saveGuest(cardID: String, context: ExchangeContextModel) throws {
        try guestLibrary.save(cardID: cardID, context: context)
    }
    func perform(_ action: () async throws -> Void) async {
        guard !busy else { return }
        busy = true
        defer { busy = false }
        do { try await action() } catch { message = error.localizedDescription }
    }
    func token() throws -> String {
        guard let session else { throw APIError.loginRequired }
        return session.sessionToken
    }
    func login(challengeID: String, code: String) async throws {
        struct Request: Encodable { let challengeId: String; let code: String }
        let result: SessionModel = try await api.request("POST", "auth/sessions",
            body: JSONEncoder().encode(Request(challengeId: challengeID, code: code)))
        try vault.save(result)
        session = result
        cards = []; receipts = []; selectedCardID = nil
        try profileState.selectAccount(result.profileId)
        do { try await refresh() } catch {
            guard session != nil else { throw APIError.loginRequired }
            message = "로그인했습니다. 서버 자료를 불러오지 못했습니다: \(error.localizedDescription)"
        }
    }
    func clearLocalSession() throws {
        try vault.clear()
        session = nil; cards = []; receipts = []; selectedCardID = nil
        try profileState.selectAccount(nil)
    }
    func logout() async throws {
        let access = try token()
        do {
            let _: EmptyResponse = try await authenticated("DELETE", "auth/session", token: access)
            try clearLocalSession()
        } catch {
            try clearLocalSession()
            message = "이 기기에서 로그아웃했습니다. 서버 세션 폐기는 확인하지 못했습니다."
        }
    }
    private func authenticated<Response: Decodable & Sendable>(_ method: String, _ path: String,
                                                               token: String, body: Data? = nil) async throws -> Response {
        guard session?.sessionToken == token else { throw CancellationError() }
        do {
            let response: Response = try await api.request(method, path, token: token, body: body)
            guard session?.sessionToken == token else { throw CancellationError() }
            return response
        } catch {
            if (error as? APIError) == .status(401), session?.sessionToken == token {
                try clearLocalSession()
            }
            throw error
        }
    }
    func refresh() async throws {
        let access = try token()
        let accountID = session?.profileId
        let serverProfile: ProfileModel = try await authenticated("GET", "profile", token: access)
        guard session?.profileId == accountID else { return }
        try profileState.restoreServerIfMissing(serverProfile)
        let own: Items<CardModel> = try await authenticated("GET", "cards", token: access)
        let wallet: Items<ReceiptModel> = try await authenticated("GET", "wallet", token: access)
        guard session?.profileId == accountID else { return }
        cards = own.items; receipts = wallet.items
    }
    func publish(_ request: CardRequest) async throws {
        let access = try token()
        // Explicit publish is also explicit consent to upload this private master profile.
        let _: ProfileModel = try await authenticated("PUT", "profile", token: access,
            body: JSONEncoder().encode(ProfileRequest(profile)))
        let card: CardModel = try await authenticated("POST", "cards", token: access,
            body: JSONEncoder().encode(request))
        cards.append(card); selectedCardID = card.id
    }
    func importGuests(selected: Set<String>) async throws {
        let results: Items<ImportResult> = try await authenticated("POST", "wallet/import", token: token(),
            body: JSONEncoder().encode(Items(items: guests.filter { selected.contains($0.cardId) })))
        try guestLibrary.applyImport(results.items, selected: selected)
        message = "가져오기 결과를 저장했습니다. 실패하거나 선택하지 않은 명함은 기기에 남아 있습니다."
        do { try await refresh() } catch { message = "가져오기 결과는 저장됐지만 목록을 새로고침하지 못했습니다." }
    }
    private static func fractionalDate(_ raw: String) -> Date? {
        let formatter = ISO8601DateFormatter()
        formatter.formatOptions = [.withInternetDateTime, .withFractionalSeconds]
        return formatter.date(from: raw)
    }
    func send(cardID: String, recipientID: String, context: ExchangeContextModel) async throws {
        struct Response: Decodable, Sendable { let receiptId: String; let deliveredAt: String }
        let access = try token()
        guard let accountID = session?.profileId else { throw APIError.loginRequired }
        let request = try exchangeState.request(accountID: accountID, cardID: cardID,
            recipientID: recipientID, context: context)
        let response: Response = try await authenticated("POST", "exchanges", token: access,
            body: JSONEncoder().encode(request))
        guard UUID(uuidString: response.receiptId) != nil,
              ISO8601DateFormatter().date(from: response.deliveredAt) != nil || Self.fractionalDate(response.deliveredAt) != nil else {
            throw APIError.invalidResponse
        }
        try exchangeState.complete(accountID: accountID, request: request)
        message = "서버가 전달을 확인했습니다. 상대가 읽었다는 의미는 아닙니다."
        do { try await refresh() } catch { message = "전달은 확인됐지만 목록을 새로고침하지 못했습니다. 당겨서 다시 불러오세요." }
    }
}
