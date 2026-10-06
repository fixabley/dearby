import XCTest
@testable import Dearby

/// Opt-in: the real client against an isolated local API whose mail goes to a local Mailpit.
/// Run with TEST_RUNNER_DEARBY_REAL_API_ORIGIN and TEST_RUNNER_DEARBY_REAL_MAILPIT (loopback only); never production.
final class AccountRealAPITests: XCTestCase {
    private let environment = ProcessInfo.processInfo.environment

    private func code(for email: String, mailpit: URL) async throws -> String {
        for _ in 0..<40 {
            var search = URLComponents(url: mailpit.appending(path: "api/v1/search"), resolvingAgainstBaseURL: false)!
            search.queryItems = [URLQueryItem(name: "query", value: "to:\(email)")]
            let (list, _) = try await URLSession.shared.data(from: search.url!)
            let messages = (try JSONSerialization.jsonObject(with: list) as? [String: Any])?["messages"] as? [[String: Any]] ?? []
            if let id = messages.first?["ID"] as? String {
                let (message, _) = try await URLSession.shared.data(from: mailpit.appending(path: "api/v1/message/\(id)"))
                let text = (try JSONSerialization.jsonObject(with: message) as? [String: Any])?["Text"] as? String ?? ""
                if let range = text.range(of: #"\d{6}"#, options: .regularExpression) { return String(text[range]) }
            }
            try await Task.sleep(for: .milliseconds(250))
        }
        throw XCTSkip("no mail for \(email)")
    }

    func testSignInEditProfilePublishAndSignOut() async throws {
        guard let apiRaw = environment["DEARBY_REAL_API_ORIGIN"], let mailRaw = environment["DEARBY_REAL_MAILPIT"],
              apiRaw.hasPrefix("http://127.0.0.1"), mailRaw.hasPrefix("http://127.0.0.1") else {
            throw XCTSkip("Set loopback DEARBY_REAL_API_ORIGIN and DEARBY_REAL_MAILPIT to run against a local API.")
        }
        let client = AccountClient.live(api: URL(string: apiRaw)!)
        let email = "ios-\(UUID().uuidString.lowercased())@example.test"
        let challenge = try await client.requestCode(email: email)
        let session = try await client.signIn(challengeId: challenge, code: try await code(for: email, mailpit: URL(string: mailRaw)!))
        var profile = try await client.profile(session)
        XCTAssertEqual(profile.name, "")
        let contact = AccountContact(id: UUID().uuidString.lowercased(), kind: "email", label: "이메일", value: email)
        let history = AccountHistory(id: UUID().uuidString.lowercased(), title: "테스트 활동", role: "참가자",
                                     startDate: "2026-10-01", endDate: nil, description: "")
        profile.name = "iOS 테스트"; profile.contacts = [contact]; profile.histories = [history]
        let saved = try await client.saveProfile(profile, session)
        XCTAssertEqual(saved.contacts, [contact])
        let card = try await client.publish(.init(name: "첫 명함", description: "", contactIds: [contact.id], historyIds: []), session)
        XCTAssertEqual(card.profileName, "iOS 테스트")
        XCTAssertEqual(card.contacts.map(\.id), [contact.id])
        XCTAssertTrue(card.histories.isEmpty)
        do {
            _ = try await client.publish(.init(name: "x", description: "", contactIds: [UUID().uuidString.lowercased()], historyIds: []), session)
            XCTFail("selection outside the profile must fail")
        } catch { XCTAssertEqual(error as? AccountError, .invalidInput) }
        try await client.signOut(session)
        do { _ = try await client.profile(session); XCTFail("revoked session must fail") } catch {
            XCTAssertEqual(error as? AccountError, .unauthorized)
        }
    }
}
