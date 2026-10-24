import Foundation

enum AccountError: Error, Equatable {
    /// `rejected`: a 400 with the server's user-facing ProblemDetail `detail` (contact rules, unapproved history, own card…).
    /// `conflict`: 409 — the profile changed elsewhere (`If-Match`).
    /// `disabled`: 503 — the server has this feature switched off (e.g. Google sign-in without its client ID), unlike a
    /// network failure or 5xx, which is `unavailable`.
    /// `consentRequired`: 403 `CONSENT_REQUIRED` — the account has not accepted the current privacy policy and terms
    /// (contract "개인정보 동의"); other 403s stay `unavailable`.
    /// `cardCodeRequired`: 403 `card-code-required`/`card-code-expired` — a card not yet saved needs a live QR code
    /// (contract "명함 QR 코드").
    /// `blocked`: 409 `BLOCKED` — the card's owner is someone I blocked (contract "명함 신고·차단"); other 409s stay `conflict`.
    /// `lastSignInMethod`: 409 `LAST_SIGN_IN_METHOD` — the passkey or linked account is the only way left to sign in, so it
    /// cannot be removed (contract "설정"); other 409s stay `conflict`.
    case unauthorized, invalidInput, rejected(String), notFound, conflict, rateLimited, disabled, unavailable, consentRequired, cardCodeRequired, blocked, lastSignInMethod
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
    /// With a `session` (Bearer) the same calls add a passkey to that account instead (contract "설정 — 패스키 추가").
    func registrationOptions(session: TokenPair? = nil) async throws -> PasskeyChallenge<Passkey.CreationOptions> {
        try await send("auth/passkeys/registration/options", method: "POST", body: [String: String](), session: session, as: PasskeyChallenge.self)
    }
    func register(challengeId: String, credential: Passkey.Credential) async throws -> TokenPair {
        try await send("auth/passkeys/registration", method: "POST", body: PasskeyAnswer(challengeId: challengeId, credential: credential), as: TokenPair.self)
    }
    /// Finishes adding a passkey to the signed-in account; the answer body (if any) is not needed, the session stays as it is.
    func addPasskey(challengeId: String, credential: Passkey.Credential, _ session: TokenPair) async throws {
        _ = try await request("auth/passkeys/registration", method: "POST", body: PasskeyAnswer(challengeId: challengeId, credential: credential), session: session)
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
    /// Google sign-in, step 1 (contract "구글 로그인", native SDK): a single-use nonce. `disabled` when the server has no
    /// Google client IDs configured (503).
    func googleNonce() async throws -> GoogleNonce {
        try await send("auth/google/nonce", method: "POST", as: GoogleNonce.self)
    }
    /// Google sign-in, step 2: the SDK's ID token with that nonce. 200: this Google account already has a Dearby account.
    /// 202: first time (`GoogleLink`). 401 means the token was rejected; the flow starts over.
    func signInWithGoogle(idToken: String, nonce: String) async throws -> GoogleSignInResult {
        let (data, status) = try await request("auth/google", method: "POST", body: ["idToken": idToken, "nonce": nonce])
        do {
            if status == 202 { return .newGoogleAccount(try JSONDecoder().decode(GoogleLink.self, from: data)) }
            return .signedIn(try JSONDecoder().decode(TokenPair.self, from: data))
        } catch { throw AccountError.unavailable }
    }
    /// A new Dearby account for a first-time Google account (201).
    func registerWithGoogle(_ link: GoogleLink) async throws -> TokenPair {
        try await send("auth/google/register", method: "POST", body: ["linkToken": link.linkToken], as: TokenPair.self)
    }
    /// Links a first-time Google account to the signed-in (passkey) account (204). 400 if it already has another Google account.
    func linkGoogle(_ link: GoogleLink, _ session: TokenPair) async throws {
        _ = try await request("me/identities/google", method: "POST", body: ["linkToken": link.linkToken], session: session)
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
    /// The account summary for 설정 (`GET /v1/me`, contract "설정"): when it was made and how many ways it can sign in.
    func me(_ session: TokenPair) async throws -> AccountSummary {
        try await send("me", session: session, as: AccountSummary.self)
    }
    /// Linked Apple/Google accounts (`GET /v1/me/identities`).
    func identities(_ session: TokenPair) async throws -> [LinkedIdentity] {
        try await send("me/identities", session: session, as: ItemList<LinkedIdentity>.self).items
    }
    /// 연결 해제 (`DELETE /v1/me/identities/{apple|google}`, 204); `lastSignInMethod` when nothing else could sign in.
    func unlinkIdentity(_ provider: String, _ session: TokenPair) async throws {
        _ = try await request("me/identities/\(provider)", method: "DELETE", session: session)
    }
    /// This account's passkeys (`GET /v1/me/passkeys`).
    func passkeys(_ session: TokenPair) async throws -> [PasskeySummary] {
        try await send("me/passkeys", session: session, as: ItemList<PasskeySummary>.self).items
    }
    /// 이름 바꾸기 (`PATCH /v1/me/passkeys/{id} {label}`, 1~60 characters) → the updated item.
    func renamePasskey(_ id: String, label: String, _ session: TokenPair) async throws -> PasskeySummary {
        try await send("me/passkeys/\(id)", method: "PATCH", body: ["label": label], session: session, as: PasskeySummary.self)
    }
    /// 삭제 (`DELETE /v1/me/passkeys/{id}`, 204); `lastSignInMethod` when it is the only way left to sign in, `notFound` for someone else's id.
    func deletePasskey(_ id: String, _ session: TokenPair) async throws {
        _ = try await request("me/passkeys/\(id)", method: "DELETE", session: session)
    }
    /// Records consent to the current privacy policy and terms (`POST /v1/me/consents`, 204). A stale version is `rejected`.
    func recordConsent(privacyVersion: String, termsVersion: String, _ session: TokenPair) async throws {
        _ = try await request("me/consents", method: "POST", body: ConsentInput(privacyVersion: privacyVersion, termsVersion: termsVersion), session: session)
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
    /// Picker searches for the history steps. Organizations, programs and roles need a session; activities are public and are asked with
    /// `forHistory=true` so events shorter than a week stay out (contract "활동 이력에서 단기 행사 제외" 2026-10-20); discovery uses `ActivityClient`.
    func searchActivityInfo(_ kind: ActivityInfoKind, query: String, parentID: Int? = nil, _ session: TokenPair?) async throws -> ActivityInfoPage {
        var items = [URLQueryItem(name: "q", value: query), URLQueryItem(name: "size", value: "20")]
        switch kind {
        case .organizations, .jobs: if let parentID { items.append(URLQueryItem(name: "parentId", value: String(parentID))) }
        case .programs: if let parentID { items.append(URLQueryItem(name: "organizationId", value: String(parentID))) }
        case .activities:
            items.append(URLQueryItem(name: "forHistory", value: "true"))
            if let parentID { items.append(URLQueryItem(name: "programId", value: String(parentID))) }
        case .roles: break
        }
        var components = URLComponents()
        components.queryItems = items
        return try await send("\(kind.rawValue)\(components.percentEncodedQuery.map { "?" + $0 } ?? "")", session: session, as: ActivityInfoPage.self)
    }
    func publish(_ card: CardInput, _ session: TokenPair) async throws -> PublishedCard {
        try await send("cards", method: "POST", body: card, session: session, as: PublishedCard.self)
    }
    /// 이 명함 고치기 (`PUT /v1/cards/:id`, contract "명함 고치기는 같은 명함을 수정" 2026-10-20): the same body as `publish` rebuilds that
    /// card's snapshot from the current profile; the id, QR and link stay. `notFound` when the card was deleted meanwhile.
    func updateCard(_ cardID: String, _ card: CardInput, _ session: TokenPair) async throws -> PublishedCard {
        try await send("cards/\(cardID)", method: "PUT", body: card, session: session, as: PublishedCard.self)
    }
    /// 이 명함 지우기 (`DELETE /v1/cards/:id`, contract "명함·지갑"): the public link then answers 404; 204 on success.
    func deleteCard(_ cardID: String, _ session: TokenPair) async throws {
        _ = try await request("cards/\(cardID)", method: "DELETE", session: session)
    }
    /// Your non-withdrawn cards in creation order, so the newest is last.
    func cards(_ session: TokenPair) async throws -> [PublishedCard] {
        try await send("cards", session: session, as: CardList.self).items
    }
    /// Public `GET /v1/cards/:id?code=` (contract "명함 QR 코드"; the request the web `/cards/:id` makes): 403 without a live code
    /// (`cardCodeRequired`) unless the caller owns or already holds it, 404 when deleted.
    func publicCard(_ cardID: String, code: String?, _ session: TokenPair?) async throws -> PublishedCard {
        var components = URLComponents()
        components.queryItems = code.map { [URLQueryItem(name: "code", value: $0)] }
        return try await send("cards/\(cardID)\(components.percentEncodedQuery.map { "?" + $0 } ?? "")", session: session, as: PublishedCard.self)
    }
    func wallet(_ session: TokenPair) async throws -> Wallet {
        try await send("wallet", session: session, as: Wallet.self)
    }
    /// `PUT /v1/wallet/cards/:cardId {activityId}` (contract "명함 교환"): saves a card arriving by QR to the account; 201 or 200 with
    /// the Receipt, 404 for an unknown or withdrawn card, 409 `OWN_CARD` (surfaces as `conflict`) for my own card.
    func receiveCard(_ cardID: String, activityID: Int64?, code: String?, _ session: TokenPair) async throws -> Wallet.Receipt {
        try await send("wallet/cards/\(cardID)", method: "PUT", body: ReceiveInput(activityId: activityID, code: code), session: session, as: Wallet.Receipt.self)
    }
    /// `POST /v1/cards/:id/qr-codes` (contract "명함 QR 코드"): a fresh code for the QR screen, usable by many until expired.
    func issueQRCode(_ cardID: String, _ session: TokenPair) async throws -> QRCode {
        try await send("cards/\(cardID)/qr-codes", method: "POST", session: session, as: QRCode.self)
    }
    /// `DELETE /v1/cards/:id/qr-codes/:codeId`: 204, also when it already expired.
    func expireQRCode(_ cardID: String, codeID: String, _ session: TokenPair) async throws {
        _ = try await request("cards/\(cardID)/qr-codes/\(codeID)", method: "DELETE", session: session)
    }
    struct QRCode: Decodable, Equatable, Sendable {
        let id: String
        let code: String
        let expiresAt: String
        /// When the server drops the code by itself (24 hours); nil if the timestamp does not parse.
        var expiry: Date? {
            let formatter = ISO8601DateFormatter()
            formatter.formatOptions = [.withInternetDateTime, .withFractionalSeconds]
            return formatter.date(from: expiresAt) ?? ISO8601DateFormatter().date(from: expiresAt)
        }
    }
    /// `POST /v1/cards/:id/reports` (contract "명함 신고·차단"): 201, or 200 for a repeat within 24 hours.
    func reportCard(_ cardID: String, reason: CardReportReason, detail: String?, _ session: TokenPair) async throws {
        _ = try await request("cards/\(cardID)/reports", method: "POST", body: ReportInput(reason: reason.rawValue, detail: detail), session: session)
    }
    /// `POST /v1/wallet/blocks {cardId}`: removes that owner's cards from my wallet and refuses them from now on.
    func block(cardID: String, _ session: TokenPair) async throws -> BlockedPerson {
        try await send("wallet/blocks", method: "POST", body: BlockInput(cardId: cardID), session: session, as: BlockedPerson.self)
    }
    func blocks(_ session: TokenPair) async throws -> [BlockedPerson] {
        try await send("wallet/blocks", session: session, as: ItemList<BlockedPerson>.self).items
    }
    /// `DELETE /v1/wallet/blocks/:id`: the removed cards do not come back.
    func unblock(_ id: String, _ session: TokenPair) async throws {
        _ = try await request("wallet/blocks/\(id)", method: "DELETE", session: session)
    }
    /// `POST /v1/wallet/cards/:cardId/reciprocate {myCardId}`: hands one of my cards back to that card's owner (same activity); 201.
    func reciprocate(_ cardID: String, myCardID: String, _ session: TokenPair) async throws {
        _ = try await request("wallet/cards/\(cardID)/reciprocate", method: "POST", body: ReciprocateInput(myCardId: myCardID), session: session)
    }

    /// 내 활동 (contract "내 활동 서버 저장" 2026-10-22): the signed-in account's activities with their stage statuses.
    func myActivities(_ session: TokenPair) async throws -> [MyActivity] {
        try await send("me/activities", session: session, as: ItemList<MyActivity>.self).items
    }
    /// Adds the activity (an existing one stays as it is); 404 for an unpublished activity.
    func putMyActivity(_ activityID: Int64, _ session: TokenPair) async throws -> MyActivity {
        try await send("me/activities/\(activityID)", method: "PUT", body: [String: String](), session: session, as: MyActivity.self)
    }
    /// Removes the activity with its stage statuses (204, also when it was not there).
    func deleteMyActivity(_ activityID: Int64, _ session: TokenPair) async throws {
        _ = try await request("me/activities/\(activityID)", method: "DELETE", session: session)
    }
    /// One stage's status: an admin status by `statusId`, or a default one (no id) by `kind`. 400 when either does not belong.
    func putMyActivityStatus(_ activityID: Int64, scheduleID: Int64, statusID: Int64?, kind: String, _ session: TokenPair) async throws -> MyActivity {
        let body = statusID.map { StatusInput(statusId: $0, kind: nil) } ?? StatusInput(statusId: nil, kind: kind)
        return try await send("me/activities/\(activityID)/schedules/\(scheduleID)", method: "PUT", body: body, session: session, as: MyActivity.self)
    }
    /// Uploads the records kept before signing in, once; the server keeps its own value for activities it already has.
    func importMyActivities(_ items: [MyActivityImport], _ session: TokenPair) async throws -> [MyActivity] {
        try await send("me/activities/import", method: "POST", body: ItemsInput(items: items), session: session, as: ItemList<MyActivity>.self).items
    }

    /// 추천 설정 (contract "추천 설정 퍼널·지원 자격 정보·추천" 2026-10-24): nil when the account has none yet (404 or `completedAt` and lists
    /// all empty are both "none"; the caller decides). Other failures throw.
    func recommendationPreferences(_ session: TokenPair) async throws -> RecommendationPreferences? {
        do { return try await send("me/recommendation-preferences", session: session, as: RecommendationPreferences.self) } catch AccountError.notFound { return nil }
    }
    func saveRecommendationPreferences(_ preferences: RecommendationPreferences, _ session: TokenPair) async throws -> RecommendationPreferences {
        try await send("me/recommendation-preferences", method: "PUT", body: preferences, session: session, as: RecommendationPreferences.self)
    }
    /// 지원 자격 정보: `notFound` when the server does not have the endpoint yet.
    func eligibilityProfile(_ session: TokenPair) async throws -> EligibilityProfile {
        try await send("me/eligibility-profile", session: session, as: EligibilityProfile.self)
    }
    /// A missing or stale `consentVersion` is a 400 (`rejected`/`invalidInput`): the sheet asks again.
    func saveEligibilityProfile(_ input: EligibilityInput, _ session: TokenPair) async throws -> EligibilityProfile {
        try await send("me/eligibility-profile", method: "PUT", body: input, session: session, as: EligibilityProfile.self)
    }
    /// 지원 자격 정보 지우기: the values and the consent record go together.
    func deleteEligibilityProfile(_ session: TokenPair) async throws {
        _ = try await request("me/eligibility-profile", method: "DELETE", session: session)
    }

    /// The funnel's answers; `completedAt` is set even when everything was skipped (`나중에`), so the funnel shows once.
    struct RecommendationPreferences: Codable, Equatable, Sendable {
        var fieldTagIds: [Int64] = []
        var categories: [String] = []
        var traitTagIds: [Int64] = []
        var completedAt: String?
        /// Finished with nothing picked.
        var isEmpty: Bool { fieldTagIds.isEmpty && categories.isEmpty && traitTagIds.isEmpty }
    }
    /// `GET /v1/me/eligibility-profile`; every value nil when nothing was entered. Values use the eligibility vocabulary (contract "대상자 조건").
    struct EligibilityProfile: Decodable, Equatable, Sendable {
        struct Consent: Decodable, Equatable, Sendable { let version: String; let agreedAt: String? }
        var consent: Consent?
        var educationStatus: String?
        var employment: String?
        var schoolYear: Int?
        var birthYear: Int?
        static let empty = EligibilityProfile()
    }
    /// PUT replaces every value, so nil fields go out as `null` (a status change can drop the 학년).
    struct EligibilityInput: Encodable, Equatable, Sendable {
        let consentVersion: String
        var educationStatus: String?
        var employment: String?
        var schoolYear: Int?
        var birthYear: Int?
        private enum CodingKeys: String, CodingKey { case consentVersion, educationStatus, employment, schoolYear, birthYear }
        func encode(to encoder: Encoder) throws {
            var container = encoder.container(keyedBy: CodingKeys.self)
            try container.encode(consentVersion, forKey: .consentVersion)
            try container.encode(educationStatus, forKey: .educationStatus)
            try container.encode(employment, forKey: .employment)
            try container.encode(schoolYear, forKey: .schoolYear)
            try container.encode(birthYear, forKey: .birthYear)
        }
    }

    /// One 내 활동 record. `statusId` is nil for a default status; `kind` is WAITING·PASSED·FAILED·OTHER.
    struct MyActivity: Decodable, Equatable, Sendable {
        struct Status: Codable, Equatable, Sendable { let scheduleId: Int64; var statusId: Int64? = nil; let kind: String }
        let activityId: Int64
        let createdAt: String?
        let statuses: [Status]
    }
    struct MyActivityImport: Encodable, Equatable, Sendable {
        struct Status: Encodable, Equatable, Sendable { let scheduleId: Int64; let kind: String }
        let activityId: Int64
        let statuses: [Status]
    }
    /// Nil fields are left out of the JSON (synthesized `encodeIfPresent`).
    private struct StatusInput: Encodable { let statusId: Int64?; let kind: String? }
    private struct ItemsInput: Encodable { let items: [MyActivityImport] }

    /// `POST /v1/cards`·`PUT /v1/cards/:id`: `template` is the app's built-in markdown template the server fills in at publish time.
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
    /// From a 202 of `auth/google`: `linkToken` lasts ten minutes and is spent by `registerWithGoogle` or `linkGoogle`.
    /// `email` is only present when Google verified it; `displayName` is Google's `name`.
    struct GoogleLink: Decodable, Equatable, Sendable { let linkToken: String; let email: String?; let displayName: String? }
    enum GoogleSignInResult: Equatable, Sendable { case signedIn(TokenPair), newGoogleAccount(GoogleLink) }
    struct GoogleNonce: Decodable, Sendable { let nonce: String }
    /// Nil optionals are left out of the JSON (synthesized `encodeIfPresent`).
    private struct AppleAnswer: Encodable {
        let challengeId: String; let identityToken: String; let displayName: String?; let authorizationCode: String?
    }
    private struct PasskeyAnswer: Encodable { let challengeId: String; let credential: Passkey.Credential }
    /// RFC 9457 ProblemDetail: `detail` is the user-facing sentence; `code` names the case for the few the app branches on.
    private struct ProblemBody: Decodable { let type: String?; let detail: String?; let code: String? }
    private struct ConsentInput: Encodable { let privacyVersion: String; let termsVersion: String }
    private struct CardList: Decodable { let items: [PublishedCard] }
    private struct ItemList<Item: Decodable>: Decodable { let items: [Item] }
    /// `activityId` is left out of the JSON when nil (synthesized `encodeIfPresent`).
    private struct ReceiveInput: Encodable { let activityId: Int64?; let code: String? }
    private struct ReciprocateInput: Encodable { let myCardId: String }
    private struct ReportInput: Encodable { let reason: String; let detail: String? }
    private struct BlockInput: Encodable { let cardId: String }

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
        case 403:
            let problem = try? JSONDecoder().decode(ProblemBody.self, from: data)
            if problem?.code == "CONSENT_REQUIRED" { throw AccountError.consentRequired }
            if let type = problem?.type, type.hasSuffix("card-code-required") || type.hasSuffix("card-code-expired") { throw AccountError.cardCodeRequired }
            throw AccountError.unavailable
        case 404: throw AccountError.notFound
        case 409:
            switch (try? JSONDecoder().decode(ProblemBody.self, from: data))?.code {
            case "LAST_SIGN_IN_METHOD": throw AccountError.lastSignInMethod
            case "BLOCKED": throw AccountError.blocked
            default: throw AccountError.conflict
            }
        case 429: throw AccountError.rateLimited
        case 503: throw AccountError.disabled
        default: throw AccountError.unavailable
        }
    }
}
