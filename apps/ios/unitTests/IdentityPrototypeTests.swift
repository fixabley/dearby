import XCTest
@testable import Dearby

@MainActor final class IdentityPrototypeTests: XCTestCase {
    func testSaveAndSendOnlyMutateCurrentSession() {
        let state = IdentityViewModel()
        let card = state.received[0]
        state.save(card)
        state.save(card)
        XCTAssertEqual(state.received.count, 5)
        XCTAssertTrue(state.savedCardIDs.contains(card.id))
        XCTAssertFalse(state.reciprocalIDs.contains(card.id))
        state.send(to: card)
        XCTAssertTrue(state.reciprocalIDs.contains(card.id))
        state.signedIn = true
        let fresh = IdentityViewModel()
        XCTAssertFalse(fresh.signedIn)
        XCTAssertFalse(fresh.reciprocalIDs.contains(card.id))
        XCTAssertTrue(fresh.savedCardIDs.isEmpty)
    }
}
