import XCTest
@testable import Dearby

/// Records requests and answers from a script; stands in for the API in unit tests.
private final class FakeTransport: @unchecked Sendable {
    var requests: [URLRequest] = []
    var answers: [(Int, String)] = []
    func callAsFunction(_ request: URLRequest) throws -> (Data, HTTPURLResponse) {
        requests.append(request)
        let (status, body) = answers.isEmpty ? (500, "{}") : answers.removeFirst()
        return (Data(body.utf8), HTTPURLResponse(url: request.url!, statusCode: status, httpVersion: nil, headerFields: nil)!)
    }
}
private final class MemoryStore: SessionStore, @unchecked Sendable {
    var session: AccountSession?
    func load() throws -> AccountSession? { session }
    func save(_ session: AccountSession) throws { self.session = session }
    func clear() throws { session = nil }
}

@MainActor final class AccountTests: XCTestCase {
    private let api = URL(string: "https://api.example.test")!
    private let challenge = "e1000000-0000-4000-8000-000000000001"
    private func client(_ fake: FakeTransport) -> AccountClient { AccountClient(api: api) { try fake($0) } }

    func testSignInFlowStoresTheSessionAndSendsBearer() async throws {
        let fake = FakeTransport(), store = MemoryStore()
        fake.answers = [(202, #"{"challengeId":"\#(challenge)","expiresAt":"2026-10-06T00:05:00Z"}"#),
                        (200, #"{"sessionToken":"token-1","profileId":"p1"}"#),
                        (200, #"{"id":"p1","name":"","job":"","introduction":"","contacts":[],"histories":[],"updatedAt":"2026-10-06T00:00:00Z"}"#)]
        let account = AccountViewModel(client: client(fake), vault: store)
        XCTAssertEqual(account.phase, .signedOut)
        await account.requestCode("  Me@Example.Test ")
        XCTAssertEqual(account.phase, .codeSent)
        XCTAssertEqual(fake.requests[0].url?.absoluteString, "https://api.example.test/v1/auth/challenges")
        XCTAssertEqual(String(data: fake.requests[0].httpBody!, encoding: .utf8), #"{"email":"me@example.test"}"#)
        await account.verify("123456")
        XCTAssertEqual(account.phase, .signedIn)
        XCTAssertEqual(store.session?.sessionToken, "token-1")
        let profile = try await account.authorized { try await account.client.profile($0) }
        XCTAssertEqual(profile.name, "")
        XCTAssertEqual(fake.requests[2].value(forHTTPHeaderField: "Authorization"), "Bearer token-1")
        XCTAssertNil(fake.requests[0].value(forHTTPHeaderField: "Authorization"))
    }
    func testWrongCodeKeepsTheChallengeAndBadInputNeverCallsTheAPI() async {
        let fake = FakeTransport()
        fake.answers = [(202, #"{"challengeId":"\#(challenge)"}"#), (401, "{}")]
        let account = AccountViewModel(client: client(fake), vault: MemoryStore())
        await account.requestCode("no-at-sign")
        XCTAssertTrue(fake.requests.isEmpty)
        await account.requestCode("me@example.test")
        await account.verify("12ab56")
        XCTAssertEqual(fake.requests.count, 1)
        await account.verify("000000")
        XCTAssertEqual(account.phase, .codeSent)
        XCTAssertEqual(account.message, "인증번호가 맞지 않거나 만료됐어요.")
    }
    func testRateLimitAndExpiredSessionSignOut() async {
        let fake = FakeTransport(), store = MemoryStore()
        store.session = AccountSession(sessionToken: "old", profileId: "p1")
        fake.answers = [(401, "{}"), (429, "{}")]
        let account = AccountViewModel(client: client(fake), vault: store)
        XCTAssertEqual(account.phase, .signedIn)
        do { _ = try await account.authorized { try await account.client.profile($0) }; XCTFail("expected 401") } catch {}
        XCTAssertEqual(account.phase, .signedOut)
        XCTAssertNil(store.session)
        await account.requestCode("me@example.test")
        XCTAssertEqual(account.message, "요청이 많아요. 잠시 후 다시 시도해 주세요.")
    }
    func testSignOutForgetsTheSessionEvenWhenTheServerFails() async {
        let fake = FakeTransport(), store = MemoryStore()
        store.session = AccountSession(sessionToken: "t", profileId: "p1")
        fake.answers = [(500, "{}")]
        let account = AccountViewModel(client: client(fake), vault: store)
        await account.signOut()
        XCTAssertEqual(fake.requests.first?.httpMethod, "DELETE")
        XCTAssertNil(store.session)
        XCTAssertEqual(account.phase, .signedOut)
    }
    func testProfileEncodesOngoingHistoryWithNullEndDateAndPublishSendsIDs() async throws {
        let history = AccountHistory(id: "h1", title: "t", role: "r", startDate: "2026-01-01", endDate: nil, description: "")
        let json = String(data: try JSONEncoder().encode(history), encoding: .utf8)!
        XCTAssertTrue(json.contains(#""endDate":null"#), json)
        let fake = FakeTransport()
        fake.answers = [(201, #"{"id":"c1","ownerId":"p1","name":"명함","description":"","profileName":"김","job":"","introduction":"","contacts":[],"histories":[],"createdAt":"2026-10-06T00:00:00Z"}"#)]
        let card = try await client(fake).publish(.init(name: "명함", description: "", contactIds: ["a"], historyIds: []),
                                                  AccountSession(sessionToken: "t", profileId: "p1"))
        XCTAssertEqual(card.id, "c1")
        let body = try JSONSerialization.jsonObject(with: fake.requests[0].httpBody!) as? [String: Any]
        XCTAssertEqual(body?["contactIds"] as? [String], ["a"])
    }
    func testKeychainVaultRoundTripsUnderItsOwnService() throws {
        let vault = SessionVault(service: "dearby.account.session.test")
        try vault.clear()
        XCTAssertNil(try vault.load())
        try vault.save(AccountSession(sessionToken: "secret", profileId: "p1"))
        XCTAssertEqual(try vault.load()?.sessionToken, "secret")
        try vault.clear()
        XCTAssertNil(try vault.load())
    }

    private let storedProfile = #"{"id":"p1","name":"저장된 이름","job":"","introduction":"","contacts":[{"id":"c-old","kind":"phone","label":"전화번호","value":"010-0000-0000"}],"histories":[{"id":"h-old","title":"캠프","role":"","startDate":"2025-01","endDate":null,"description":""}],"updatedAt":"2026-10-06T00:00:00Z"}"#
    private let cardBody = #"{"id":"card-1","name":"내 명함","description":"","profileName":"김지민","job":"","contacts":[],"histories":[],"createdAt":"2026-10-06T00:00:00Z"}"#
    private func json(_ request: URLRequest) throws -> [String: Any] {
        try XCTUnwrap(JSONSerialization.jsonObject(with: XCTUnwrap(request.httpBody)) as? [String: Any])
    }
    func testGuestDraftMergesOntoTheAccountAndKeepsUnseenRowsPrivate() async throws {
        let fake = FakeTransport(), store = MemoryStore()
        let account = AccountViewModel(client: client(fake), vault: store)
        let model = CardPublishModel(account: account)
        model.draft.name = "김지민"
        let email = try XCTUnwrap(model.draft.contacts.firstIndex { $0.kind == "email" })
        model.draft.contacts[email].value = "me@example.test"
        store.session = AccountSession(sessionToken: "t", profileId: "p1")
        let signedIn = AccountViewModel(client: client(fake), vault: store)
        let resumed = CardPublishModel(account: signedIn)
        resumed.draft = model.draft
        fake.answers = [(200, storedProfile), (200, storedProfile), (201, cardBody)]
        await resumed.continueAfterSignIn()
        XCTAssertEqual(fake.requests.map(\.httpMethod), ["GET", "PUT", "POST"])
        let profile = try json(fake.requests[1])
        XCTAssertEqual(profile["name"] as? String, "김지민")
        let saved = try XCTUnwrap(profile["contacts"] as? [[String: Any]]).compactMap { $0["value"] as? String }
        XCTAssertEqual(Set(saved), ["010-0000-0000", "me@example.test"])
        XCTAssertEqual((profile["histories"] as? [[String: Any]])?.count, 1)
        let card = try json(fake.requests[2])
        XCTAssertEqual((card["contactIds"] as? [String])?.count, 1)
        XCTAssertNotEqual((card["contactIds"] as? [String])?.first, "c-old")
        XCTAssertEqual(card["historyIds"] as? [String], [])
        guard case .published(let published) = resumed.phase else { return XCTFail("\(resumed.phase)") }
        XCTAssertEqual(published.id, "card-1")
    }
    func testCardFailureAfterProfileSaveRetriesOnlyTheCard() async throws {
        let fake = FakeTransport(), store = MemoryStore()
        store.session = AccountSession(sessionToken: "t", profileId: "p1")
        let model = CardPublishModel(account: AccountViewModel(client: client(fake), vault: store))
        fake.answers = [(200, storedProfile)]
        await model.load()
        XCTAssertEqual(model.draft.name, "저장된 이름")
        model.draft.job = "기획"
        fake.answers = [(200, storedProfile), (500, "{}")]
        await model.publish()
        XCTAssertEqual(model.phase, .failed("프로필은 저장했어요. 명함 발행만 다시 시도해 주세요."))
        fake.requests = []
        fake.answers = [(201, cardBody)]
        await model.publish()
        XCTAssertEqual(fake.requests.map(\.httpMethod), ["POST"])
        guard case .published = model.phase else { return XCTFail("\(model.phase)") }
    }
}
