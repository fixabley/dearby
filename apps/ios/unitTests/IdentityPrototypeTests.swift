import XCTest
@testable import Dearby

@MainActor final class IdentityPrototypeTests: XCTestCase {
    func testCreateCardIncludesOnlySelectedFields() {
        let state = IdentityViewModel()
        let card = state.makeCard(name: "공유", contactIDs: ["email"], historyIDs: ["camp"])
        XCTAssertEqual(card.contacts.map(\.id), ["email"])
        XCTAssertEqual(card.histories.map(\.id), ["camp"])
        XCTAssertEqual(state.selectedCardID, card.id)
        XCTAssertEqual(state.cards.count, 4)
    }
    func testEditingReplacesSelectedCardWithoutCreatingAnother() {
        let state = IdentityViewModel()
        let id = state.cards[0].id
        let edited = state.makeCard(name: "수정한 명함", editingID: id, contactIDs: ["chat"], historyIDs: [])
        XCTAssertEqual(edited.id, id)
        XCTAssertEqual(state.cards.count, 3)
        XCTAssertEqual(state.cards[0].name, "수정한 명함")
        XCTAssertEqual(state.cards[0].contacts.map(\.id), ["chat"])
        XCTAssertTrue(state.cards[0].histories.isEmpty)
    }
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
