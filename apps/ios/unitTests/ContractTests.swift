import XCTest
import SwiftData
@testable import Dearby

@MainActor final class ContractTests: XCTestCase {
    func testExpiredSessionClearsAuthWithoutDeletingDraftsOrGuests() async throws {
        let configuration = URLSessionConfiguration.ephemeral
        configuration.protocolClasses = [ContractURLProtocol.self]
        let transport = URLSession(configuration: configuration)
        defer { transport.invalidateAndCancel() }
        let client = APIClient(baseURL: URL(string: "https://expired.test"), session: transport)
        let vault = SessionVault(namespace: "https://expired.test")
        try vault.save(SessionModel(sessionToken: "expired-test-token", profileId: "account-a"))
        defer { try? vault.clear() }
        let storage = try LocalStore(container: ModelContainer(for: StoredDocument.self,
            configurations: ModelConfiguration(isStoredInMemoryOnly: true)))
        let state = try AppState(store: storage, api: client)
        try state.saveProfile(ProfileModel(name: "Private draft"))
        let cardID = UUID().uuidString
        try state.saveGuest(cardID: cardID, context: .init())
        do { try await state.refresh(); XCTFail("Expected expired session rejection") } catch {}
        XCTAssertNil(state.session)
        XCTAssertNil(try vault.load())
        XCTAssertEqual(state.guests.map(\.cardId), [cardID])
        try state.profileState.selectAccount("account-a")
        XCTAssertEqual(state.profile.name, "Private draft")
    }
    func testContactActionsRejectUnsafeSchemesAndEmailHeaderInjection() {
        for value in ["javascript:alert(1)", "http://insecure.test", "file:///tmp/data", "https://user:password@example.test"] {
            let contact = ContactModel(kind: "github", value: value)
            if case .unavailable = contact.action {} else { XCTFail("Unsafe URL must not open") }
        }
        let injected = ContactModel(kind: "email", value: "name@example.test?bcc=hidden@example.test")
        if case .unavailable = injected.action {} else { XCTFail("Email header injection must be rejected") }
        if case .copy(let value) = ContactModel(kind: "kakao", value: "dearby-test-id").action {
            XCTAssertEqual(value, "dearby-test-id")
        } else { XCTFail("Kakao ID must copy, not navigate") }
        if case .open(let url) = ContactModel(kind: "phone", value: "+82 10-1234-5678").action {
            XCTAssertEqual(url.scheme, "tel")
        } else { XCTFail("Valid phone action must be available") }
    }
    func testCardLinkContextRoundTripAndStrictRejection() throws {
        let id = UUID().uuidString
        let link = CardLink(cardID: id, context: ExchangeContextModel(label: "디자인 & 협업"))
        let url = try XCTUnwrap(link.url)
        XCTAssertEqual(try CardLink(parsing: url), link)
        for raw in ["dearby://card/\(id)?label=a&label=b", "dearby://card/\(id)?label=a&activityId=\(id)",
                    "dearby://card/\(id)/extra", "dearby://evil/\(id)", "https://card/\(id)",
                    "dearby://user@card/\(id)", "dearby://card/\(id)#fragment", "dearby://card/\(id)?unknown=x",
                    "dearby://card/\(id)?activityId=invalid", "dearby://card/\(id)?label=" + String(repeating: "a", count: 201)] {
            XCTAssertThrowsError(try CardLink(parsing: XCTUnwrap(URL(string: raw))), raw)
        }
    }
    func testNullableContractFieldsEncodeExplicitNull() throws {
        let context = try JSONSerialization.jsonObject(with: JSONEncoder().encode(ExchangeContextModel())) as? [String: Any]
        XCTAssertTrue(context?["activityId"] is NSNull)
        XCTAssertTrue(context?["label"] is NSNull)
        let history = try JSONSerialization.jsonObject(with: JSONEncoder().encode(HistoryModel())) as? [String: Any]
        XCTAssertTrue(history?["endDate"] is NSNull)
    }
    func testHTTPUsesV1ExactlyOnceAndBearerWithoutCookiePersistence() async throws {
        let configuration = URLSessionConfiguration.ephemeral
        configuration.protocolClasses = [ContractURLProtocol.self]
        let session = URLSession(configuration: configuration)
        defer { session.invalidateAndCancel() }
        for origin in ["https://example.test", "https://example.test/v1"] {
            let client = APIClient(baseURL: URL(string: origin), session: session)
            let response: Echo = try await client.request("GET", "cards", token: "test-token")
            XCTAssertEqual(response.path, "/v1/cards")
            XCTAssertEqual(response.authorization, "Bearer test-token")
        }
    }
    func testHTTPFailureAndMalformedSuccessNeverDecodeAsDelivery() async throws {
        let configuration = URLSessionConfiguration.ephemeral
        configuration.protocolClasses = [ContractURLProtocol.self]
        let session = URLSession(configuration: configuration)
        defer { session.invalidateAndCancel() }
        let client = APIClient(baseURL: URL(string: "https://example.test"), session: session)
        for path in ["unauthorized", "failure", "malformed"] {
            do { let _: Echo = try await client.request("POST", path); XCTFail("Must reject \(path)") } catch { /* Rejection is the expected contract. */ }
        }
    }
}
private struct Echo: Decodable, Sendable { let path: String; let authorization: String }
private final class ContractURLProtocol: URLProtocol, @unchecked Sendable {
    override class func canInit(with request: URLRequest) -> Bool { true }
    override class func canonicalRequest(for request: URLRequest) -> URLRequest { request }
    override func startLoading() {
        guard let url = request.url else { return }
        let status: Int
        if url.host == "expired.test" { status = 401 } else {
            switch url.lastPathComponent { case "unauthorized": status = 401; case "failure": status = 503; default: status = 200 }
        }
        guard let response = HTTPURLResponse(url: url, statusCode: status, httpVersion: nil, headerFields: nil) else { return }
        let payload: Data
        if url.lastPathComponent == "malformed" { payload = Data("{}".utf8) } else { payload = (try? JSONSerialization.data(withJSONObject: ["path": url.path,
            "authorization": request.value(forHTTPHeaderField: "Authorization") ?? ""])) ?? Data() }
        client?.urlProtocol(self, didReceive: response, cacheStoragePolicy: .notAllowed)
        client?.urlProtocol(self, didLoad: payload)
        client?.urlProtocolDidFinishLoading(self)
    }
    override func stopLoading() {}
}
