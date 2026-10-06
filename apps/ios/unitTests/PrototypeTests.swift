import XCTest
@testable import Dearby

@MainActor final class PrototypeTests: XCTestCase {
    func testFixedActivitiesAndSessionOnlyApplicationState() {
        let state = CatalogViewModel()
        XCTAssertEqual(state.activities.map(\.title), ["Dearby 개발자 컨퍼런스", "Dearby 메이커 캠프", "Dearby 커뮤니티 밋업"])
        XCTAssertEqual(state.activities.map(\.demoStatus), ["예시 모집중", "예시 모집중", "예시 모집예정"])
        XCTAssertEqual(state.activities.map(\.participationType), [.registration, .selection, .registration])
        XCTAssertEqual(state.activities.map(\.location), ["서울 코엑스", "서울", "온라인"])
        XCTAssertTrue(state.activities.allSatisfy { $0.cost == "무료" && $0.schedules.first?.timeZone == "Asia/Seoul" })
        XCTAssertEqual(state.activities.map(\.applicationUrl), Array(repeating: "https://example.com", count: 3))
        XCTAssertEqual(state.appliedIDs, ["conference"])
        state.apply("camp", true)
        XCTAssertEqual(state.appliedActivities.map(\.id), ["conference", "camp"])
        XCTAssertEqual(CatalogViewModel().appliedIDs, ["conference"])
    }
    func testFixedBusyTimeAndZeroOverlapActivities() {
        let formatter = ISO8601DateFormatter()
        XCTAssertEqual(CalendarConflictState.exampleBusy.start, formatter.date(from: "2026-10-24T14:00:00+09:00"))
        XCTAssertEqual(CalendarConflictState.exampleBusy.end, formatter.date(from: "2026-10-24T15:00:00+09:00"))
        for (index, activity) in DemoActivities.all.enumerated() {
            let state = CalendarConflictState(schedules: activity.schedules)
            XCTAssertFalse(state.compared)
            state.compare()
            XCTAssertTrue(state.compared)
            XCTAssertEqual(state.overlaps.count, index == 0 ? 1 : 0)
            if let overlap = state.overlaps.first {
                XCTAssertEqual(overlap.start, CalendarConflictState.exampleBusy.start)
                XCTAssertEqual(overlap.end, CalendarConflictState.exampleBusy.end)
            }
            state.selected = false
            XCTAssertFalse(state.compared)
            XCTAssertTrue(state.overlaps.isEmpty)
            state.compare()
            XCTAssertFalse(state.compared)
        }
    }
    func testTimelineAndAdvancingResults() {
        let activity = DemoActivities.all[0].schedules[0]
        let state = CalendarConflictState(schedules: [activity, activity])
        state.compare()
        let result = state.overlaps[0]
        XCTAssertEqual(result.minutes, 60)
        XCTAssertEqual(result.time(result.gridStart), "13:00")
        XCTAssertEqual(result.time(result.gridEnd), "15:30")
        XCTAssertEqual(result.gridSteps, 5)
        XCTAssertGreaterThan(activity.end, result.gridEnd)
        XCTAssertTrue(state.advance())
        XCTAssertEqual(state.index, 1)
        XCTAssertFalse(state.advance())
        let empty = CalendarConflictState(schedules: DemoActivities.all[1].schedules)
        empty.compare()
        XCTAssertFalse(empty.advance())
    }
    func testTouchingEndpointsDoNotOverlap() {
        let activity = DemoActivities.all[0].schedules[0]
        XCTAssertFalse(CalendarOverlapState.matches(activity, DateInterval(start: activity.end, duration: 3600)))
        XCTAssertFalse(CalendarOverlapState.matches(activity, DateInterval(start: activity.start.addingTimeInterval(-3600), duration: 3600)))
        XCTAssertTrue(CalendarOverlapState.matches(activity, DateInterval(start: activity.start, duration: 3600)))
    }
    func testOnlyHTTPSLinksWithoutCredentialsAreAllowed() {
        for raw in ["http://example.com", "javascript:alert(1)", "file:///tmp/a", "dearby://card/1", "https://user:password@example.com", "https://", "//example.com"] {
            XCTAssertNil(ActivityModel.safeURL(raw), raw)
        }
        XCTAssertNotNil(ActivityModel.safeURL("https://example.com/dearby/conference"))
        XCTAssertNil(ActivityModel.safeURL(nil))
    }
}
