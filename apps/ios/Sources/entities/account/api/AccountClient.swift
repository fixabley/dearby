import Foundation

enum AccountError: Error, Equatable {
    /// `rejected`: a 400 with the server's user-facing ProblemDetail `detail` (contact rules, unapproved history, own card…).
    /// `conflict`: 409 — the profile changed elsewhere (`If-Match`).
    case unauthorized, invalidInput, rejected(String), notFound, conflict, rateLimited, unavailable
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
    /// Sign in with Apple, step 1: a single-use nonce that lasts ten minutes.
    func appleNonce() async throws -> AppleChallenge {
        try await send("auth/apple/nonce", method: "POST", as: AppleChallenge.self)
    }
    /// 200: this Apple account already has a Dearby account. 202: first time; the user picks new or link (`AppleLink`).
    func signInWithApple(challengeId: String, credential: AppleSignIn.Credential) async throws -> AppleSignInResult {
        let body = AppleAnswer(challengeId: challengeId, identityToken: credential.identityToken,
                               displayName: credential.displayName, authorizationCode: credential.authorizationCode)
        let (data, status) = try await request("auth/apple", method: "POST", body: body)
        do {
            if status == 202 { return .newAppleAccount(try JSONDecoder().decode(AppleLink.self, from: data)) }
            return .signedIn(try JSONDecoder().decode(TokenPair.self, from: data))
        } catch { throw AccountError.unavailable }
    }
    /// A new Dearby account for a first-time Apple account (201).
    func registerWithApple(_ link: AppleLink) async throws -> TokenPair {
        try await send("auth/apple/register", method: "POST", body: ["linkToken": link.linkToken], as: TokenPair.self)
    }
    /// Links a first-time Apple account to the signed-in (passkey) account (204). 400 if it already has another Apple account.
    func linkApple(_ link: AppleLink, _ session: TokenPair) async throws {
        _ = try await request("me/identities/apple", method: "POST", body: ["linkToken": link.linkToken], session: session)
    }
    /// Rotates: the refresh token sent is spent whether or not the answer arrives.
    func refresh(_ tokens: TokenPair) async throws -> TokenPair {
        try await send("auth/refresh", method: "POST", body: ["refreshToken": tokens.refreshToken], as: TokenPair.self)
    }
    func logout(_ tokens: TokenPair) async throws {
        _ = try await request("auth/logout", method: "POST", body: ["refreshToken": tokens.refreshToken])
    }
    /// Deletes the account and everything it owns (contract "계정 삭제"); 204. The server also drops the refresh token.
    func deleteAccount(_ session: TokenPair) async throws {
        _ = try await request("me", method: "DELETE", session: session)
    }
    func profile(_ session: TokenPair) async throws -> AccountProfile {
        try await send("profile", session: session, as: AccountProfile.self)
    }
    /// `If-Match` carries the version the edit started from; a 409 means the profile changed elsewhere.
    func saveProfile(_ profile: AccountProfile, ifMatch: Int?, _ session: TokenPair) async throws -> AccountProfile {
        try await send("profile", method: "PUT", body: profile, session: session, headers: ifMatch.map { ["If-Match": String($0)] } ?? [:], as: AccountProfile.self)
    }
    func profileVersions(cursor: Int?, _ session: TokenPair) async throws -> ProfileVersionList {
        try await send("profile/versions" + (cursor.map { "?cursor=\($0)&size=20" } ?? "?size=20"), session: session, as: ProfileVersionList.self)
    }
    func profileVersion(_ version: Int, _ session: TokenPair) async throws -> ProfileVersionDetail {
        try await send("profile/versions/\(version)", session: session, as: ProfileVersionDetail.self)
    }
    /// Makes a new version from an old one (the history is never rewritten).
    func restoreProfile(_ version: Int, _ session: TokenPair) async throws -> AccountProfile {
        try await send("profile/versions/\(version)/restore", method: "POST", session: session, as: AccountProfile.self)
    }
    /// Multipart upload of one evidence file (max 3 per history, 5MB, image or PDF).
    func uploadEvidence(historyID: String, fileName: String, contentType: String, data: Data, _ session: TokenPair) async throws -> EvidenceMeta {
        let boundary = "dearby-" + UUID().uuidString
        var body = Data()
        body.append("--\(boundary)\r\nContent-Disposition: form-data; name=\"file\"; filename=\"\(fileName.replacingOccurrences(of: "\"", with: ""))\"\r\nContent-Type: \(contentType)\r\n\r\n".data(using: .utf8)!)
        body.append(data)
        body.append("\r\n--\(boundary)--\r\n".data(using: .utf8)!)
        let (response, _) = try await request("profile/histories/\(historyID)/evidence", method: "POST", raw: body,
                                              contentType: "multipart/form-data; boundary=\(boundary)", session: session)
        do { return try JSONDecoder().decode(EvidenceMeta.self, from: response) } catch { throw AccountError.unavailable }
    }
    func deleteEvidence(historyID: String, evidenceID: String, _ session: TokenPair) async throws {
        _ = try await request("profile/histories/\(historyID)/evidence/\(evidenceID)", method: "DELETE", session: session)
    }
    /// The file bytes, for an in-app preview.
    func evidence(historyID: String, evidenceID: String, _ session: TokenPair) async throws -> Data {
        try await request("profile/histories/\(historyID)/evidence/\(evidenceID)", method: "GET", session: session).0
    }
    /// Picker searches for the history steps. Organizations, programs and roles need a session; activities are public.
    func searchActivityInfo(_ kind: ActivityInfoKind, query: String, parentID: Int? = nil, _ session: TokenPair?) async throws -> ActivityInfoPage {
        var items = [URLQueryItem(name: "q", value: query), URLQueryItem(name: "size", value: "20")]
        switch kind {
        case .organizations, .jobs: if let parentID { items.append(URLQueryItem(name: "parentId", value: String(parentID))) }
        case .programs: if let parentID { items.append(URLQueryItem(name: "organizationId", value: String(parentID))) }
        case .activities: if let parentID { items.append(URLQueryItem(name: "programId", value: String(parentID))) }
        case .roles: break
        }
        var components = URLComponents()
        components.queryItems = items
        return try await send("\(kind.rawValue)\(components.percentEncodedQuery.map { "?" + $0 } ?? "")", session: session, as: ActivityInfoPage.self)
    }
    func publish(_ card: CardInput, _ session: TokenPair) async throws -> PublishedCard {
        try await send("cards", method: "POST", body: card, session: session, as: PublishedCard.self)
    }
    /// Your non-withdrawn cards in creation order, so the newest is last.
    func cards(_ session: TokenPair) async throws -> [PublishedCard] {
        try await send("cards", session: session, as: CardList.self).items
    }
    func wallet(_ session: TokenPair) async throws -> Wallet {
        try await send("wallet", session: session, as: Wallet.self)
    }

    /// `POST /v1/cards`: `template` is the decoration markdown the server fills in at publish time.
    struct CardInput: Encodable, Equatable, Sendable {
        let name: String
        let description: String
        let template: String
        let contactIds: [String]
        let historyIds: [String]
    }
    enum ActivityInfoKind: String, Sendable { case organizations, programs, activities, roles, jobs }
    /// `{challengeId, options}` from an options call; the challenge is single use and lasts five minutes.
    struct PasskeyChallenge<Options: Decodable & Sendable>: Decodable, Sendable {
        let challengeId: String
        let options: Options
    }
    struct AppleChallenge: Decodable, Sendable { let challengeId: String; let nonce: String }
    /// From a 202: `linkToken` lasts ten minutes and is spent by `registerWithApple` or `linkApple`.
    /// `email` may be an Apple private relay address.
    struct AppleLink: Decodable, Equatable, Sendable { let linkToken: String; let email: String? }
    enum AppleSignInResult: Equatable, Sendable { case signedIn(TokenPair), newAppleAccount(AppleLink) }
    /// Nil optionals are left out of the JSON (synthesized `encodeIfPresent`).
    private struct AppleAnswer: Encodable {
        let challengeId: String; let identityToken: String; let displayName: String?; let authorizationCode: String?
    }
    private struct PasskeyAnswer: Encodable { let challengeId: String; let credential: Passkey.Credential }
    /// RFC 9457 ProblemDetail: `detail` is the user-facing sentence.
    private struct ProblemBody: Decodable { let detail: String? }
    private struct CardList: Decodable { let items: [PublishedCard] }

    private func send<Response: Decodable>(_ path: String, method: String = "GET", body: (any Encodable)? = nil,
                                           session: TokenPair? = nil, headers: [String: String] = [:], as type: Response.Type) async throws -> Response {
        let (data, _) = try await request(path, method: method, body: body, session: session, headers: headers)
        do { return try JSONDecoder().decode(type, from: data) } catch { throw AccountError.unavailable }
    }
    private func request(_ path: String, method: String, body: (any Encodable)? = nil, raw: Data? = nil, contentType: String? = nil,
                         session: TokenPair? = nil, headers: [String: String] = [:]) async throws -> (Data, Int) {
        // Paths may carry a query string; appending(path:) would escape the "?".
        var request = URLRequest(url: URL(string: "v1/" + path, relativeTo: api.appending(path: ""))!.absoluteURL, cachePolicy: .reloadIgnoringLocalCacheData, timeoutInterval: 30)
        request.httpMethod = method
        request.setValue("application/json", forHTTPHeaderField: "Accept")
        if let body {
            request.setValue("application/json", forHTTPHeaderField: "Content-Type")
            request.httpBody = try JSONEncoder().encode(body)
        } else if let raw {
            request.setValue(contentType, forHTTPHeaderField: "Content-Type")
            request.httpBody = raw
        }
        for (name, value) in headers { request.setValue(value, forHTTPHeaderField: name) }
        if let session { request.setValue("Bearer \(session.accessToken)", forHTTPHeaderField: "Authorization") }
        let data: Data, response: HTTPURLResponse
        do { (data, response) = try await transport(request) } catch { throw AccountError.unavailable }
        switch response.statusCode {
        case 200..<300: return (data, response.statusCode)
        case 400:
            if let detail = (try? JSONDecoder().decode(ProblemBody.self, from: data))?.detail, !detail.isEmpty { throw AccountError.rejected(detail) }
            throw AccountError.invalidInput
        case 401: throw AccountError.unauthorized
        case 404: throw AccountError.notFound
        case 409: throw AccountError.conflict
        case 429: throw AccountError.rateLimited
        default: throw AccountError.unavailable
        }
    }
}
