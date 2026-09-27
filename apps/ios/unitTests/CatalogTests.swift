import XCTest
import SwiftData
@testable import Dearby

@MainActor final class CatalogTests: XCTestCase {
    private func store(url: URL? = nil) throws -> LocalStore {
        let config = url.map { ModelConfiguration(url: $0) } ?? ModelConfiguration(isStoredInMemoryOnly: true)
        return try LocalStore(container: ModelContainer(for: StoredDocument.self, configurations: config))
    }
    private func client() -> APIClient {
        let config = URLSessionConfiguration.ephemeral
        config.protocolClasses = [CatalogURLProtocol.self]
        return APIClient(baseURL: URL(string: "https://catalog.test"), session: URLSession(configuration: config))
    }
    func testFreshnessDeadlineOpeningAndVerificationBoundaries() throws {
        let now = Date(timeIntervalSince1970: 1_790_460_000)
        func activity(_ changes: [String: Any] = [:]) throws -> ActivityModel {
            try CatalogFixture.catalog(now: now, changes: changes).activities[0]
        }
        XCTAssertTrue(try activity().isOpen(at: now))
        for changes: [String: Any] in [
            ["isRecruiting": false], ["freshness": "stale"], ["freshness": "unavailable"],
            ["recruitmentStatus": "unknown"], ["validUntil": CatalogFixture.date(now)],
            ["sourceCheckedAt": CatalogFixture.date(now.addingTimeInterval(1))],
            ["sourceCheckedAt": CatalogFixture.date(now.addingTimeInterval(-86_400))],
            ["recruitmentStartAt": CatalogFixture.date(now.addingTimeInterval(1))],
            ["recruitmentEndAt": CatalogFixture.date(now)], ["recruitmentEndAt": "invalid"],
            ["validUntil": NSNull()], ["sourceCheckedAt": NSNull()]
        ] { XCTAssertFalse(try activity(changes).isOpen(at: now), "\(changes)") }
        XCTAssertTrue(try activity(["recruitmentStartAt": CatalogFixture.date(now)]).isOpen(at: now))
        XCTAssertFalse(try activity().isOpen(at: now.addingTimeInterval(3600)))
        XCTAssertEqual(try activity(["recruitmentEndAt": CatalogFixture.date(now)]).status(at: now), "모집 마감")
    }
    func testExactWireDTOAndUnknownSchedulesDoNotInventTimes() throws {
        let catalog = try CatalogFixture.catalog()
        try catalog.validate()
        let activity = try XCTUnwrap(catalog.activities.first)
        XCTAssertNil(activity.cost)
        XCTAssertNil(activity.schedules[0].startAt)
        XCTAssertNil(activity.schedules[0].endAt)
        XCTAssertEqual(activity.schedules[0].dateLabel, "공식 날짜만 공개")
        XCTAssertThrowsError(try CatalogFixture.catalog(changes: ["programId": UUID().uuidString]).validate())
        XCTAssertThrowsError(try CatalogFixture.catalog(changes: ["participationType": "guaranteed"]))
        for raw in ["file:///secret", "javascript:alert(1)", "dearby://card/123", "https://user:pass@example.com", "https://"] {
            XCTAssertNil(ActivityModel.safeURL(raw))
        }
        XCTAssertNotNil(ActivityModel.safeURL("https://if.kakao.com/2026"))
        XCTAssertNotNil(ActivityModel.safeURL("http://example.com"))
    }
    func testCacheFailurePreservationAndSuccessfulEmptyReplacement() async throws {
        let storage = try store()
        let api = client()
        defer { api.session.invalidateAndCancel() }
        let state = try CatalogState(store: storage, api: api)
        XCTAssertNil(state.catalog)
        CatalogURLProtocol.responses.append(status: 200, data: try CatalogFixture.data())
        await state.refresh()
        XCTAssertEqual(state.catalog?.activities.count, 1)
        XCTAssertFalse(state.fromCache)
        XCTAssertNil(state.error)
        let first = state.catalog
        CatalogURLProtocol.responses.append(status: 503, data: Data())
        await state.refresh()
        XCTAssertEqual(state.catalog, first)
        XCTAssertTrue(state.fromCache)
        XCTAssertNotNil(state.error)
        XCTAssertFalse(state.loading)
        let restored = try CatalogState(store: storage, api: api)
        XCTAssertEqual(restored.catalog, first)
        XCTAssertTrue(restored.fromCache)
        CatalogURLProtocol.responses.append(status: 200, data: Data("{}".utf8))
        await state.refresh()
        XCTAssertEqual(state.catalog, first)
        CatalogURLProtocol.responses.append(status: 200, data: try CatalogFixture.data(empty: true))
        await state.refresh()
        XCTAssertEqual(state.catalog?.activities, [])
        XCTAssertFalse(state.fromCache)
        XCTAssertNil(state.error)
    }
    func testCorruptCacheDoesNotBlockLibraryOrNetworkRecovery() async throws {
        let storage = try store()
        let api = client()
        defer { api.session.invalidateAndCancel() }
        let previous = try CatalogState(store: storage, api: api)
        try previous.toggleProgram(CatalogFixture.programID)
        try storage.write(["broken": "cache"], key: "catalog.v1.https://catalog.test")
        let recovered = try CatalogState(store: storage, api: api)
        XCTAssertNil(recovered.catalog)
        XCTAssertNotNil(recovered.error)
        XCTAssertEqual(recovered.local.programIDs, [CatalogFixture.programID])
        CatalogURLProtocol.responses.append(status: 200, data: try CatalogFixture.data())
        await recovered.refresh()
        XCTAssertEqual(recovered.catalog?.activities.count, 1)
        XCTAssertNil(recovered.error)
    }
    func testFailedCacheCommitDoesNotPublishResponse() async throws {
        let storage = try store()
        let api = client()
        defer { api.session.invalidateAndCancel() }
        let state = try CatalogState(store: storage, api: api)
        CatalogURLProtocol.responses.append(status: 200, data: try CatalogFixture.data())
        await state.refresh()
        let first = state.catalog
        storage.beforeSave = { throw CocoaError(.fileWriteOutOfSpace) }
        CatalogURLProtocol.responses.append(status: 200, data: try CatalogFixture.data(empty: true))
        await state.refresh()
        XCTAssertEqual(state.catalog, first)
        XCTAssertEqual(try CatalogState(store: storage, api: api).catalog, first)
        XCTAssertNotNil(state.error)
    }
    func testLibraryFailureRollbackAndAppliedCorrection() throws {
        let storage = try store()
        let state = try CatalogState(store: storage, api: APIClient(baseURL: nil))
        try state.toggleProgram(CatalogFixture.programID)
        try state.toggleOrganization(CatalogFixture.organizationID)
        try state.report(.applied, activityID: CatalogFixture.activityID)
        let committed = state.local
        storage.beforeSave = { throw CocoaError(.fileWriteOutOfSpace) }
        XCTAssertThrowsError(try state.toggleProgram(CatalogFixture.programID))
        XCTAssertThrowsError(try state.toggleOrganization(CatalogFixture.organizationID))
        XCTAssertThrowsError(try state.report(.notApplied, activityID: CatalogFixture.activityID))
        XCTAssertEqual(state.local, committed)
        XCTAssertEqual(try CatalogState(store: storage, api: APIClient(baseURL: nil)).local, committed)
        storage.beforeSave = nil
        try state.report(.notApplied, activityID: CatalogFixture.activityID)
        XCTAssertEqual(state.local.applications[CatalogFixture.activityID], .notApplied)
        try state.toggleProgram(CatalogFixture.programID)
        XCTAssertTrue(state.local.programIDs.isEmpty)
        XCTAssertEqual(state.local.organizationIDs, [CatalogFixture.organizationID])
    }
    func testCatalogAndLibrarySurviveRealDiskReopen() async throws {
        let url = FileManager.default.temporaryDirectory.appendingPathComponent(UUID().uuidString + ".store")
        defer { for suffix in ["", "-shm", "-wal"] { try? FileManager.default.removeItem(atPath: url.path + suffix) } }
        let api = client()
        defer { api.session.invalidateAndCancel() }
        do {
            let state = try CatalogState(store: store(url: url), api: api)
            CatalogURLProtocol.responses.append(status: 200, data: try CatalogFixture.data())
            await state.refresh()
            try state.toggleProgram(CatalogFixture.programID)
            try state.toggleOrganization(CatalogFixture.organizationID)
            try state.report(.applied, activityID: CatalogFixture.activityID)
        }
        let restored = try CatalogState(store: store(url: url), api: api)
        XCTAssertEqual(restored.catalog?.activities.count, 1)
        XCTAssertTrue(restored.fromCache)
        XCTAssertEqual(restored.local.programIDs, [CatalogFixture.programID])
        XCTAssertEqual(restored.local.organizationIDs, [CatalogFixture.organizationID])
        XCTAssertEqual(restored.local.applications[CatalogFixture.activityID], .applied)
        let otherOrigin = try CatalogState(store: store(url: url), api: APIClient(baseURL: nil))
        XCTAssertNil(otherOrigin.catalog, "API namespaces cannot exchange catalog cache")
        XCTAssertEqual(otherOrigin.local, restored.local, "Device library is independent of login")
    }
}

enum CatalogFixture {
    static let organizationID = "9c41ec80-c90e-45ea-bdf4-f33abc6f7e3a"
    static let programID = "a6a70db1-d214-468a-90a0-4dc5939021fe"
    static let activityID = "df856623-ef7b-47e5-bc91-5e7fa37e384d"
    static func date(_ date: Date) -> String { ISO8601DateFormatter().string(from: date) }
    static func catalog(now: Date = Date(), changes: [String: Any] = [:]) throws -> CatalogModel {
        try JSONDecoder().decode(CatalogModel.self, from: data(now: now, changes: changes))
    }
    static func data(now: Date = Date(), changes: [String: Any] = [:], empty: Bool = false) throws -> Data {
        var activity: [String: Any] = [
            "id": activityID, "organizationId": organizationID, "programId": programID,
            "title": "계약 테스트 활동", "summary": "테스트 target 전용", "participationType": "selection",
            "recruitmentStatus": "open", "isRecruiting": true, "recruitmentStartAt": NSNull(),
            "recruitmentEndAt": NSNull(), "dateLabel": "2026년", "location": NSNull(), "cost": NSNull(),
            "audience": NSNull(), "qualification": NSNull(), "roles": [],
            "schedules": [["id": "e3d6efab-ff52-4c52-bb7a-70e79dcb518a", "title": "행사", "startAt": NSNull(),
                           "endAt": NSNull(), "dateLabel": "공식 날짜만 공개", "timeZone": "Asia/Seoul"]],
            "officialUrl": "https://example.test/source", "applicationUrl": "https://example.test/apply",
            "sourceCheckedAt": date(now.addingTimeInterval(-60)), "validUntil": date(now.addingTimeInterval(3600)),
            "freshness": "verified", "sourceNote": "단위 테스트 전용"
        ]
        activity.merge(changes) { _, new in new }
        return try JSONSerialization.data(withJSONObject: [
            "generatedAt": date(now),
            "organizations": empty ? [] : [["id": organizationID, "name": "테스트 조직", "description": ""]],
            "programs": empty ? [] : [["id": programID, "organizationId": organizationID, "title": "테스트 프로그램", "description": ""]],
            "activities": empty ? [] : [activity]
        ])
    }
}
private final class CatalogURLProtocol: URLProtocol, @unchecked Sendable {
    static let responses = CatalogResponses()
    override class func canInit(with request: URLRequest) -> Bool { true }
    override class func canonicalRequest(for request: URLRequest) -> URLRequest { request }
    override func startLoading() {
        guard let url = request.url else { return }
        let next = Self.responses.take()
        guard request.httpMethod == "GET", url.path == "/v1/catalog",
              request.value(forHTTPHeaderField: "Authorization") == nil,
              let response = HTTPURLResponse(url: url, statusCode: next.0, httpVersion: nil, headerFields: nil) else {
            client?.urlProtocol(self, didFailWithError: URLError(.badURL)); return
        }
        client?.urlProtocol(self, didReceive: response, cacheStoragePolicy: .notAllowed)
        client?.urlProtocol(self, didLoad: next.1)
        client?.urlProtocolDidFinishLoading(self)
    }
    override func stopLoading() {}
}
private final class CatalogResponses: @unchecked Sendable {
    private let lock = NSLock()
    private var queue: [(Int, Data)] = []
    func append(status: Int, data: Data) { lock.withLock { queue.append((status, data)) } }
    func take() -> (Int, Data) { lock.withLock { queue.isEmpty ? (500, Data()) : queue.removeFirst() } }
}
