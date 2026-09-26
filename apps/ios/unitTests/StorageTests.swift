import XCTest
import SwiftData
@testable import Dearby

@MainActor final class StorageTests: XCTestCase {
    private func store() throws -> LocalStore {
        try LocalStore(container: ModelContainer(for: StoredDocument.self,
            configurations: ModelConfiguration(isStoredInMemoryOnly: true)))
    }
    func testProfileSaveFailureRetainsCommittedValueAndDraft() throws {
        let storage = try store()
        let profile = try ProfileState(store: storage)
        var draft = ProfileModel(name: "Before")
        try profile.save(draft)
        storage.beforeSave = { throw CocoaError(.fileWriteOutOfSpace) }
        draft.name = "After"
        XCTAssertThrowsError(try profile.save(draft))
        XCTAssertEqual(profile.value.name, "Before")
        XCTAssertEqual(try storage.read(ProfileModel.self, key: "profile.guest")?.name, "Before")
        XCTAssertEqual(draft.name, "After")
    }
    func testAccountProfilesCannotLeakIntoAnotherAccount() throws {
        let state = try ProfileState(store: store())
        try state.save(ProfileModel(name: "Guest draft"))
        try state.selectAccount("account-a")
        XCTAssertEqual(state.value.name, "")
        try state.save(ProfileModel(name: "Account A"))
        try state.selectAccount("account-b")
        XCTAssertEqual(state.value.name, "")
        try state.restoreServerIfMissing(ProfileModel(name: "Account B"))
        XCTAssertEqual(state.value.name, "Account B")
        try state.selectAccount("account-a")
        XCTAssertEqual(state.value.name, "Account A")
        try state.selectAccount(nil)
        XCTAssertEqual(state.value.name, "Guest draft")
    }
    func testServerRefreshDoesNotOverwriteOfflineEdit() throws {
        let state = try ProfileState(store: store())
        try state.selectAccount("account")
        try state.save(ProfileModel(name: "Offline edit"))
        try state.restoreServerIfMissing(ProfileModel(name: "Server"))
        XCTAssertEqual(state.value.name, "Offline edit")
    }
    func testGuestPartialImportRetainsFailedAndUnselectedAndDeduplicates() throws {
        let storage = try store()
        let state = try GuestLibraryState(store: storage)
        let ids = (0..<3).map { _ in UUID().uuidString }
        for id in ids { try state.save(cardID: id, context: ExchangeContextModel()) }
        try state.save(cardID: ids[0], context: ExchangeContextModel())
        XCTAssertEqual(state.items.count, 3)
        try state.applyImport([
            ImportResult(cardId: ids[0], status: "imported", receiptId: UUID().uuidString),
            ImportResult(cardId: ids[1], status: "failed"),
            ImportResult(cardId: ids[2], status: "imported", receiptId: UUID().uuidString)
        ], selected: Set(ids.prefix(2)))
        XCTAssertEqual(Set(state.items.map(\.cardId)), Set(ids.suffix(2)))
        XCTAssertEqual(try GuestLibraryState(store: storage).items, state.items)
    }
    func testImportPersistenceFailurePreservesAllOriginalIDs() throws {
        let storage = try store()
        let state = try GuestLibraryState(store: storage)
        let id = UUID().uuidString
        try state.save(cardID: id, context: ExchangeContextModel())
        storage.beforeSave = { throw CocoaError(.fileWriteOutOfSpace) }
        XCTAssertThrowsError(try state.applyImport([
            ImportResult(cardId: id, status: "alreadySaved", receiptId: UUID().uuidString)
        ], selected: [id]))
        XCTAssertEqual(state.items.map(\.cardId), [id])
        XCTAssertEqual(try GuestLibraryState(store: storage).items.map(\.cardId), [id])
    }
    func testMalformedSuccessRetainsGuest() throws {
        let state = try GuestLibraryState(store: store())
        let id = UUID().uuidString
        try state.save(cardID: id, context: ExchangeContextModel())
        try state.applyImport([ImportResult(cardId: id, status: "imported")], selected: [id])
        XCTAssertEqual(state.items.count, 1)
    }
    func testSelectedPublicFieldsRequestContainsOnlyIDs() throws {
        let request = CardRequest(name: "Public", description: "", contactIds: ["selected"], historyIds: [])
        let data = try JSONEncoder().encode(request)
        let object = try XCTUnwrap(JSONSerialization.jsonObject(with: data) as? [String: Any])
        XCTAssertEqual(Set(object.keys), ["name", "description", "contactIds", "historyIds"])
        XCTAssertEqual(object["contactIds"] as? [String], ["selected"])
        XCTAssertEqual(object["historyIds"] as? [String], [])
    }
    func testProfilePutOmitsServerOwnedFields() throws {
        let data = try JSONEncoder().encode(ProfileRequest(ProfileModel(name: "Name")))
        let object = try XCTUnwrap(JSONSerialization.jsonObject(with: data) as? [String: Any])
        XCTAssertNil(object["id"]); XCTAssertNil(object["updatedAt"])
        XCTAssertEqual(Set(object.keys), ["name", "job", "introduction", "contacts", "histories"])
    }
    func testAmbiguousExchangeRetrySurvivesStateRecreationAndIsAccountBound() throws {
        let storage = try store()
        let first = try ExchangeState(store: storage).request(accountID: "a", cardID: "c", recipientID: "r", context: .init())
        let retry = try ExchangeState(store: storage).request(accountID: "a", cardID: "c", recipientID: "r", context: .init())
        XCTAssertEqual(first.requestId, retry.requestId)
        let other = try ExchangeState(store: storage).request(accountID: "b", cardID: "c", recipientID: "r", context: .init())
        XCTAssertNotEqual(first.requestId, other.requestId)
        try ExchangeState(store: storage).complete(accountID: "a")
        let next = try ExchangeState(store: storage).request(accountID: "a", cardID: "c", recipientID: "r", context: .init())
        XCTAssertNotEqual(first.requestId, next.requestId)
    }
    func testUnconfiguredAPIAndInvalidHTTPAreExplicit() async throws {
        XCTAssertNil(APIClient.validatedURL("http://example.com/v1"))
        XCTAssertNil(APIClient.validatedURL("https://name:password@example.com"))
        do {
            let _: CardModel = try await APIClient(baseURL: nil).request("GET", "cards/a")
            XCTFail("Must not fabricate success")
        } catch { XCTAssertTrue(error is APIError) }
    }
    func testRealDiskRestoration() throws {
        let url = FileManager.default.temporaryDirectory.appendingPathComponent(UUID().uuidString + ".store")
        defer {
            for suffix in ["", "-shm", "-wal"] { try? FileManager.default.removeItem(atPath: url.path + suffix) }
        }
        let configuration = ModelConfiguration(url: url)
        do {
            let storage = try LocalStore(container: ModelContainer(for: StoredDocument.self, configurations: configuration))
            try storage.write(ProfileModel(name: "Persisted"), key: "profile.guest")
        }
        let reopened = try LocalStore(container: ModelContainer(for: StoredDocument.self, configurations: configuration))
        XCTAssertEqual(try reopened.read(ProfileModel.self, key: "profile.guest")?.name, "Persisted")
    }
}
