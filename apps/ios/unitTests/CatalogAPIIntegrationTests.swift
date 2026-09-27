import XCTest
import SwiftData
@testable import Dearby

@MainActor final class CatalogAPIIntegrationTests: XCTestCase {
    func testActualCatalogFetchAndDeviceRestoration() async throws {
        let api = APIClient.configured
        guard let origin = api.baseURL, ["127.0.0.1", "localhost"].contains(origin.host ?? "") else {
            throw XCTSkip("Requires explicitly configured real local catalog API")
        }
        let url = FileManager.default.temporaryDirectory.appendingPathComponent(UUID().uuidString + ".store")
        defer { for suffix in ["", "-shm", "-wal"] { try? FileManager.default.removeItem(atPath: url.path + suffix) } }
        func storage() throws -> LocalStore {
            try LocalStore(container: ModelContainer(for: StoredDocument.self, configurations: ModelConfiguration(url: url)))
        }
        var savedID = ""
        do {
            let state = try CatalogState(store: storage(), api: api)
            await state.refresh()
            XCTAssertNil(state.error)
            let catalog = try XCTUnwrap(state.catalog)
            try catalog.validate()
            XCTAssertFalse(catalog.activities.isEmpty, "Use actual imported-source API")
            let activity = try XCTUnwrap(catalog.activities.first { $0.isOpen(at: Date()) })
            savedID = activity.id
            XCTAssertEqual(activity.title, "if(kakao)26")
            XCTAssertNotNil(ActivityModel.safeURL(activity.officialUrl))
            try state.toggleProgram(activity.programId)
            try state.toggleOrganization(activity.organizationId)
            try state.report(.applied, activityID: activity.id)
            let attachment = XCTAttachment(string: "Actual GET /v1/catalog: \(catalog.activities.count) activities; "
                + "\(catalog.activities.filter { $0.isOpen(at: Date()) }.count) current; selected \(activity.id); "
                + "source checked \(activity.sourceCheckedAt ?? "unknown")")
            attachment.name = "Real catalog HTTP evidence"
            attachment.lifetime = .keepAlways
            add(attachment)
        }
        let reopened = try CatalogState(store: storage(), api: api)
        XCTAssertTrue(reopened.fromCache)
        XCTAssertEqual(reopened.local.applications[savedID], .applied)
        XCTAssertEqual(reopened.local.programIDs.count, 1)
        XCTAssertEqual(reopened.local.organizationIDs.count, 1)
        XCTAssertNotNil(reopened.catalog?.activities.first { $0.id == savedID })
    }
}
