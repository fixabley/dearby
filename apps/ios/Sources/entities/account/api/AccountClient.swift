import Foundation

enum AccountError: Error, Equatable {
    /// `conflict`: a card already holds the most share links (409).
    /// `ownCard`: the share points at the signed-in account's own card (422 INVALID_RECIPIENT).
    case unauthorized, invalidInput, ownCard, notFound, conflict, rateLimited, unavailable
}

/// Owner API (contract native-v1): email code sign-in, profile and card publishing.
/// Tokens and email addresses are never logged.
struct AccountClient: Sendable {
    typealias Transport = @Sendable (URLRequest) async throws -> (Data, HTTPURLResponse)
    let api: URL
    let transport: Transport

    static func live(api: URL) -> AccountClient {
        AccountClient(api: api) { request in
            let (data, response) = try await URLSession.shared.data(for: request)
            guard let http = response as? HTTPURLResponse else { throw AccountError.unavailable }
            return (data, http)
        }
    }

    func requestCode(email: String) async throws -> String {
        try await send("auth/challenges", method: "POST", body: ["email": email], as: Challenge.self).challengeId
    }
    func signIn(challengeId: String, code: String) async throws -> AccountSession {
        try await send("auth/sessions", method: "POST", body: ["challengeId": challengeId, "code": code], as: AccountSession.self)
    }
    func signOut(_ session: AccountSession) async throws {
        _ = try await request("auth/session", method: "DELETE", session: session)
    }
    func profile(_ session: AccountSession) async throws -> AccountProfile {
        try await send("profile", session: session, as: AccountProfile.self)
    }
    func saveProfile(_ profile: AccountProfile, _ session: AccountSession) async throws -> AccountProfile {
        try await send("profile", method: "PUT", body: profile, session: session, as: AccountProfile.self)
    }
    func publish(_ card: CardInput, _ session: AccountSession) async throws -> PublishedCard {
        try await send("cards", method: "POST", body: card, session: session, as: PublishedCard.self)
    }
    /// Your non-withdrawn cards in creation order, so the newest is last.
    func cards(_ session: AccountSession) async throws -> [PublishedCard] {
        try await send("cards", session: session, as: CardList.self).items
    }
    func share(_ cardID: String, activityIds: [String], _ session: AccountSession) async throws -> CardShare {
        try await send("cards/\(cardID)/shares", method: "POST", body: ["activityIds": activityIds], session: session, as: CardShare.self)
    }
    /// Saves a received share to the account's wallet (contract #141).
    func saveShare(_ id: String, _ session: AccountSession) async throws -> SavedShare {
        // An explicit empty JSON body: some HTTP stacks send a PUT body the API rejects otherwise.
        try await send("wallet/shares/\(id)", method: "PUT", body: [String: String](), session: session, as: SavedShare.self)
    }
    func wallet(_ session: AccountSession) async throws -> Wallet {
        try await send("wallet", session: session, as: Wallet.self)
    }
    /// Public: a share and its card, for links and scanned QR codes. No session.
    func publicShare(_ id: String) async throws -> ReceivedShare {
        try await send("shares/\(id)", as: ReceivedShare.self)
    }
    /// Public: a card by ID, for legacy `dearby://card/<UUID>` codes. No session.
    func publicCard(_ id: String) async throws -> PublishedCard {
        try await send("cards/\(id)", as: PublishedCard.self)
    }

    struct CardInput: Encodable, Equatable, Sendable {
        let name: String
        let description: String
        let contactIds: [String]
        let historyIds: [String]
    }
    private struct Challenge: Decodable { let challengeId: String }
    private struct ErrorBody: Decodable { struct Detail: Decodable { let code: String?; let message: String? }; let error: Detail }
    private struct CardList: Decodable { let items: [PublishedCard] }

    private func send<Response: Decodable>(_ path: String, method: String = "GET", body: (any Encodable)? = nil,
                                           session: AccountSession? = nil, as type: Response.Type) async throws -> Response {
        let data = try await request(path, method: method, body: body, session: session)
        do { return try JSONDecoder().decode(type, from: data) } catch { throw AccountError.unavailable }
    }
    private func request(_ path: String, method: String, body: (any Encodable)? = nil, session: AccountSession? = nil) async throws -> Data {
        var request = URLRequest(url: api.appending(path: "v1/" + path), cachePolicy: .reloadIgnoringLocalCacheData, timeoutInterval: 15)
        request.httpMethod = method
        request.setValue("application/json", forHTTPHeaderField: "Accept")
        if let body {
            request.setValue("application/json", forHTTPHeaderField: "Content-Type")
            request.httpBody = try JSONEncoder().encode(body)
        }
        if let session { request.setValue("Bearer \(session.sessionToken)", forHTTPHeaderField: "Authorization") }
        let data: Data, response: HTTPURLResponse
        do { (data, response) = try await transport(request) } catch { throw AccountError.unavailable }
        switch response.statusCode {
        case 200..<300: return data
        case 401: throw AccountError.unauthorized
        case 404: throw AccountError.notFound
        case 409: throw AccountError.conflict
        case 422:
            let code = (try? JSONDecoder().decode(ErrorBody.self, from: data))?.error.code
            throw code == "INVALID_RECIPIENT" ? AccountError.ownCard : AccountError.invalidInput
        case 429: throw AccountError.rateLimited
        default: throw AccountError.unavailable
        }
    }
}
