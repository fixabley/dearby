import XCTest
@testable import Dearby

@MainActor final class CatalogTests: XCTestCase {
    private let now = ISO8601DateFormatter().date(from: "2026-10-06T00:00:00Z")!
    private func loaded() async -> CatalogViewModel {
        let state = CatalogViewModel { try CatalogClient.decode(CatalogFixture.json) }
        await state.load()
        return state
    }
    private func open() throws -> ActivityModel {
        try XCTUnwrap(CatalogClient.decode(CatalogFixture.json).first { $0.id == CatalogFixture.conference })
    }
    private func with(_ change: (inout [String: Any]) -> Void) throws -> ActivityModel {
        var activity = CatalogFixture.activity(CatalogFixture.conference, "변형", day: "2026-10-24", start: "13:00", end: "17:00")
        change(&activity)
        var body = CatalogFixture.body
        body["activities"] = [activity]
        return try XCTUnwrap(CatalogClient.decode(JSONSerialization.data(withJSONObject: body)).first)
    }

    func testDecodeKeepsOrganizationsAndOnlyTimedSchedules() throws {
        let activity = try open()
        XCTAssertEqual(activity.organization, "테스트 주최")
        XCTAssertEqual(activity.schedules.count, 1)
        let dateOnly = try with { $0["schedules"] = [["id": "x", "title": "x", "startAt": NSNull(), "endAt": NSNull(),
                                                      "dateLabel": "10월 24일", "timeZone": "Asia/Seoul"]] }
        XCTAssertTrue(dateOnly.schedules.isEmpty)
        XCTAssertThrowsError(try CatalogClient.decode(Data("{}".utf8)))
    }
    /// Same boundary cases as the web `isRecruiting` tests.
    func testRecruitingMatchesTheWebRule() throws {
        XCTAssertTrue(try open().isOpen(at: now))
        let checked = ActivityModel.instant("2026-01-01T00:00:00Z")!
        XCTAssertFalse(try open().isOpen(at: checked.addingTimeInterval(-1)))
        XCTAssertFalse(try open().isOpen(at: ActivityModel.instant("2099-01-01T00:00:00Z")!))
        for change in [{ (a: inout [String: Any]) in a["freshness"] = "stale" },
                       { a in a["recruitmentStatus"] = "closed" },
                       { a in a["isRecruiting"] = false },
                       { a in a["recruitmentStartAt"] = "2026-12-01T00:00:00Z" },
                       { a in a["recruitmentEndAt"] = "2026-10-05T00:00:00Z" },
                       { a in a["validUntil"] = "invalid" },
                       { a in a["validUntil"] = NSNull() },
                       { a in a["sourceCheckedAt"] = NSNull() },
                       { a in a["recruitmentStartAt"] = "invalid" }] {
            XCTAssertFalse(try with(change).isOpen(at: now))
        }
        XCTAssertTrue(try with { $0["recruitmentStartAt"] = NSNull(); $0["recruitmentEndAt"] = NSNull() }.isOpen(at: now))
    }
    func testQuickApplyNeedsOpenRegistrationAndHTTPSLink() throws {
        XCTAssertEqual(try open().quickApplyURL(at: now)?.absoluteString, CatalogFixture.applyURL)
        XCTAssertNil(try with { $0["participationType"] = "selection" }.quickApplyURL(at: now))
        XCTAssertNotNil(try with { $0["participationType"] = "selection" }.applicationURL(at: now))
        XCTAssertNil(try with { $0["applicationUrl"] = "http://apply.example.test" }.quickApplyURL(at: now))
        XCTAssertNil(try with { $0["applicationUrl"] = NSNull() }.quickApplyURL(at: now))
        XCTAssertNil(try with { $0["recruitmentStatus"] = "scheduled" }.quickApplyURL(at: now))
    }
    func testDiscoveryShowsOnlyOpenActivitiesAndFailureNeverBecomesEmpty() async {
        let state = await loaded()
        XCTAssertEqual(state.phase, .loaded)
        XCTAssertEqual(state.discoverable.map(\.id), [CatalogFixture.conference, CatalogFixture.camp])
        XCTAssertEqual(state.activities.count, 4)
        let failing = CatalogViewModel { throw URLError(.notConnectedToInternet) }
        await failing.load()
        XCTAssertEqual(failing.phase, .failed)
        XCTAssertTrue(failing.discoverable.isEmpty)
    }
    func testMyActivitiesStartEmptyAndConfirmationNeedsApplication() async {
        let state = await loaded()
        XCTAssertTrue(state.appliedActivities.isEmpty)
        state.apply(CatalogFixture.camp, true)
        state.apply(CatalogFixture.conference, true)
        XCTAssertEqual(state.appliedActivities.map(\.id), [CatalogFixture.conference, CatalogFixture.camp])
        state.confirm(CatalogFixture.camp, true)
        XCTAssertTrue(state.confirmedIDs.contains(CatalogFixture.camp))
        state.apply(CatalogFixture.camp, false)
        state.apply(CatalogFixture.camp, true)
        XCTAssertFalse(state.confirmedIDs.contains(CatalogFixture.camp))
        state.apply(CatalogFixture.camp, false)
        state.confirm(CatalogFixture.camp, true)
        XCTAssertFalse(state.confirmedIDs.contains(CatalogFixture.camp))
    }
    func testScheduleTimesUseTheirOwnZone() {
        let start = ActivityModel.instant("2026-10-24T04:00:00Z")!
        XCTAssertEqual(ActivityText.time(start, zone: "Asia/Seoul"), "13:00")
        XCTAssertEqual(ActivityText.day(start, zone: "Asia/Seoul"), "10/24")
        XCTAssertEqual(ActivityText.time(start, zone: "UTC"), "04:00")
    }
}
