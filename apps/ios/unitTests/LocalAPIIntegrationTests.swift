import XCTest
@testable import Dearby

/// Opt-in real TCP integration, using a coordinator-owned test-only mail sink.
/// The host supplies a private ephemeral file to the Simulator Documents folder.
@MainActor final class LocalAPIIntegrationTests: XCTestCase {
    func testCrossPlatformExchangeAndReplay() async throws {
        let marker = URL.documentsDirectory.appendingPathComponent("dearby-cross-platform.json")
        guard FileManager.default.fileExists(atPath: marker.path) else { throw XCTSkip("No explicit cross-platform test marker") }
        struct Peer: Decodable { let profileId: String; let cardId: String }
        let peer = try JSONDecoder().decode(Peer.self, from: Data(contentsOf: marker))
        let storage = try LocalStore()
        let state = try AppState(store: storage)
        guard state.api.baseURL?.host == "127.0.0.1", let accountID = state.session?.profileId else {
            throw XCTSkip("Requires local API signed-in session")
        }
        try await state.refresh()
        let card = try XCTUnwrap(state.cards.first)
        let exchange = ExchangeState(store: storage)
        let request = try exchange.request(accountID: accountID, cardID: card.id,
            recipientID: peer.profileId, context: ExchangeContextModel(label: "cross-platform test"))
        try await state.send(cardID: card.id, recipientID: peer.profileId, context: request.context)
        struct Delivery: Decodable, Sendable { let receiptId: String; let deliveredAt: String }
        let first: Delivery = try await state.api.request("POST", "exchanges", token: state.token(), body: JSONEncoder().encode(request))
        let replay: Delivery = try await state.api.request("POST", "exchanges", token: state.token(), body: JSONEncoder().encode(request))
        XCTAssertEqual(first.receiptId, replay.receiptId)
        XCTAssertEqual(first.deliveredAt, replay.deliveredAt)
        try await state.refresh()
        let reciprocal = state.receipts.contains { $0.card.id == peer.cardId && $0.reciprocal }
        let evidence: [String: Any] = ["receiptId": first.receiptId, "sameReceiptOnReplay": true,
            "reverseCardReciprocal": reciprocal, "peerProfileId": peer.profileId]
        try JSONSerialization.data(withJSONObject: evidence, options: .prettyPrinted)
            .write(to: URL.documentsDirectory.appendingPathComponent("dearby-cross-platform-result.json"))
        XCTAssertTrue(reciprocal, "Android reverse delivery must appear reciprocal after wallet refresh")
    }
    func testActualLoginPublishAndPublicProjection() async throws {
        let file = URL.documentsDirectory.appendingPathComponent("dearby-integration.json")
        guard FileManager.default.fileExists(atPath: file.path) else {
            throw XCTSkip("No explicit local API/mail-sink integration fixture")
        }
        struct Fixture: Decodable { let origin: String; let challengeId: String; let code: String }
        let fixture = try JSONDecoder().decode(Fixture.self, from: Data(contentsOf: file))
        try FileManager.default.removeItem(at: file)
        let api = APIClient.configured
        guard api.baseURL?.absoluteString == fixture.origin else { XCTFail("Integration API configuration mismatch"); return }
        let state = try AppState(store: LocalStore(), api: api)
        try await state.login(challengeID: fixture.challengeId, code: fixture.code)
        XCTAssertNotNil(state.session)
        var profile = state.profile
        profile.name = "iOS 검증 계정"
        profile.job = "네이티브 개발"
        profile.introduction = "로컬 실제 API 연결 검증"
        let visible = ContactModel(kind: "email", label: "공개 이메일", value: "ios-visible@example.test")
        let hidden = ContactModel(kind: "email", label: "비공개 이메일", value: "ios-private@example.test")
        profile.contacts = [visible, hidden]
        profile.histories = [HistoryModel(title: "네이티브 개발 검증", role: "개발자", startDate: "2026-09-27")]
        try state.saveProfile(profile)
        try await state.publish(CardRequest(name: "iOS 공개 명함", description: "선택한 연락처만 공개",
            contactIds: [visible.id], historyIds: []))
        let card = try XCTUnwrap(state.cards.last)
        XCTAssertEqual(card.contacts.map(\.id), [visible.id])
        XCTAssertTrue(card.histories.isEmpty)
        let publicCard = try await state.resolveCard(card.id)
        XCTAssertEqual(publicCard.contacts.map(\.id), [visible.id])
        XCTAssertFalse(publicCard.contacts.contains { $0.value == hidden.value })
        try state.saveGuest(cardID: card.id, context: ExchangeContextModel(label: "실제 HTTP 검증"))
        try await state.importGuests(selected: [card.id])
        XCTAssertFalse(state.guests.contains { $0.cardId == card.id })
        XCTAssertTrue(state.receipts.contains { $0.card.id == card.id })
        let evidence = ["profileId": try XCTUnwrap(state.session?.profileId), "cardId": card.id,
            "checks": "real URLSession OTP fixture login, Keychain, profile PUT, selected public GET, guest import and wallet GET"]
        try JSONSerialization.data(withJSONObject: evidence, options: .prettyPrinted)
            .write(to: URL.documentsDirectory.appendingPathComponent("dearby-integration-result.json"))
        // Keep this real test account signed in so the same app can be inspected on Simulator.
    }
}
