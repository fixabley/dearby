import Foundation

enum AccountError: Error, Equatable {
    /// `invalidContacts`: the profile's required phone or email is missing or malformed (422 "Invalid contacts").
    /// `ownCard`: the share points at the signed-in account's own card (422 INVALID_RECIPIENT).
    /// `conflict`: a card already holds the most share links (409).
    case unauthorized, invalidInput, invalidContacts, ownCard, notFound, conflict, rateLimited, unavailable
}

/// Owner API (contract native-v1): passkey sign-in, profile and card publishing.
/// Tokens and contact values are never logged.
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

    /// Passkey sign-up: the server picks the passkey's `user.name` and `user.id`; the account is made on `register`.
    func registrationOptions() async throws -> PasskeyChallenge<Passkey.CreationOptions> {
        try await send("auth/passkeys/registration/options", method: "POST", body: [String: String](), as: PasskeyChallenge.self)
    }
    func register(challengeId: String, credential: Passkey.Credential) async throws -> TokenPair {
        try await send("auth/passkeys/registration", method: "POST", body: PasskeyAnswer(challengeId: challengeId, credential: credential), as: TokenPair.self)
    }
    func authenticationOptions() async throws -> PasskeyChallenge<Passkey.RequestOptions> {
        try await send("auth/passkeys/authentication/options", method: "POST", as: PasskeyChallenge.self)
    }
    func authenticate(challengeId: String, credential: Passkey.Credential) async throws -> TokenPair {
        try await send("auth/passkeys/authentication", method: "POST", body: PasskeyAnswer(challengeId: challengeId, credential: credential), as: TokenPair.self)
    }
    /// Rotates: the refresh token sent is spent whether or not the answer arrives.
    func refresh(_ tokens: TokenPair) async throws -> TokenPair {
        try await send("auth/refresh", method: "POST", body: ["refreshToken": tokens.refreshToken], as: TokenPair.self)
    }
    func logout(_ tokens: TokenPair) async throws {
        _ = try await request("auth/logout", method: "POST", body: ["refreshToken": tokens.refreshToken])
    }
    func profile(_ session: TokenPair) async throws -> AccountProfile {
        try await send("profile", session: session, as: AccountProfile.self)
    }
    func saveProfile(_ profile: AccountProfile, _ session: TokenPair) async throws -> AccountProfile {
        try await send("profile", method: "PUT", body: profile, session: session, as: AccountProfile.self)
    }
    func publish(_ card: CardInput, _ session: TokenPair) async throws -> PublishedCard {
        try await send("cards", method: "POST", body: card, session: session, as: PublishedCard.self)
    }
    /// Your non-withdrawn cards in creation order, so the newest is last.
    func cards(_ session: TokenPair) async throws -> [PublishedCard] {
        try await send("cards", session: session, as: CardList.self).items
    }
    func share(_ cardID: String, activityIds: [String], _ session: TokenPair) async throws -> CardShare {
        try await send("cards/\(cardID)/shares", method: "POST", body: ["activityIds": activityIds], session: session, as: CardShare.self)
    }
    /// Saves a received share to the account's wallet (contract #141).
    func saveShare(_ id: String, _ session: TokenPair) async throws -> SavedShare {
        // An explicit empty JSON body: some HTTP stacks send a PUT body the API rejects otherwise.
        try await send("wallet/shares/\(id)", method: "PUT", body: [String: String](), session: session, as: SavedShare.self)
    }
    func wallet(_ session: TokenPair) async throws -> Wallet {
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
    /// `{challengeId, options}` from an options call; the challenge is single use and lasts five minutes.
    struct PasskeyChallenge<Options: Decodable & Sendable>: Decodable, Sendable {
        let challengeId: String
        let options: Options
    }
    private struct PasskeyAnswer: Encodable { let challengeId: String; let credential: Passkey.Credential }
    private struct ErrorBody: Decodable { struct Detail: Decodable { let code: String?; let message: String? }; let error: Detail }
    private struct CardList: Decodable { let items: [PublishedCard] }

    private func send<Response: Decodable>(_ path: String, method: String = "GET", body: (any Encodable)? = nil,
                                           session: TokenPair? = nil, as type: Response.Type) async throws -> Response {
        let data = try await request(path, method: method, body: body, session: session)
        do { return try JSONDecoder().decode(type, from: data) } catch { throw AccountError.unavailable }
    }
    private func request(_ path: String, method: String, body: (any Encodable)? = nil, session: TokenPair? = nil) async throws -> Data {
        var request = URLRequest(url: api.appending(path: "v1/" + path), cachePolicy: .reloadIgnoringLocalCacheData, timeoutInterval: 15)
        request.httpMethod = method
        request.setValue("application/json", forHTTPHeaderField: "Accept")
        if let body {
            request.setValue("application/json", forHTTPHeaderField: "Content-Type")
            request.httpBody = try JSONEncoder().encode(body)
        }
        if let session { request.setValue("Bearer \(session.accessToken)", forHTTPHeaderField: "Authorization") }
        let data: Data, response: HTTPURLResponse
        do { (data, response) = try await transport(request) } catch { throw AccountError.unavailable }
        switch response.statusCode {
        case 200..<300: return data
        case 400: throw AccountError.invalidInput
        case 401: throw AccountError.unauthorized
        case 404: throw AccountError.notFound
        case 409: throw AccountError.conflict
        case 422:
            let detail = (try? JSONDecoder().decode(ErrorBody.self, from: data))?.error
            if detail?.code == "INVALID_RECIPIENT" { throw AccountError.ownCard }
            throw detail?.message == "Invalid contacts" ? AccountError.invalidContacts : AccountError.invalidInput
        case 429: throw AccountError.rateLimited
        default: throw AccountError.unavailable
        }
    }
}
