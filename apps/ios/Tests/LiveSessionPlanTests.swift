import XCTest
@testable import Dearby

/// 행사 당일 진행 중 표시 — 계획 로직 (contract "행사 당일 진행 중 표시", 2026-10-27 사용자 합의): `TodayEvent.pick`, `LiveSessionModel.Plan.today` and
/// `LiveSessionModel.moment` against a fixed clock. Seoul day 2026-10-27; the activity's 활동 일정 block is a timed 시간표 block that day.
@MainActor final class LiveSessionPlanTests: XCTestCase {
    typealias Plan = LiveSessionModel.Plan

    // MARK: 오늘 활동 선택

    func testPicksTodayActivityWithEarliestBlockBeforeUpcoming() {
        let late = applied("1", "우아콘 2026", block: at(10, 0)...at(18, 0))
        let early = applied("2", "데브 데이", block: at(9, 0)...at(17, 0))
        let soon = applied("3", "이틀 뒤 행사", block: at(10, 0, day: 29)...at(18, 0, day: 29))
        let picked = TodayEvent.pick([late, early, soon], favorites: [], now: at(11, 0))
        XCTAssertEqual(picked?.activity.id, "2")
        XCTAssertEqual(picked?.timing, .today)
        XCTAssertEqual(picked?.others, 2)
        XCTAssertEqual(TodayEvent.pick([soon], favorites: [], now: at(11, 0))?.timing, .upcoming(days: 2))
    }

    func testPlanTakesTheActivityTheCardPicksAndItsFavoritesOfTheDay() {
        let late = applied("1", "우아콘 2026", block: at(10, 0)...at(18, 0))
        let early = applied("2", "데브 데이", block: at(9, 0)...at(17, 0))
        let favorites = [favorite(1, activity: "1", at(13, 0)...at(13, 50)), favorite(2, activity: "2", at(14, 0)...at(14, 40))]
        let plan = Plan.today([late, early], favorites: favorites, now: at(11, 0))
        XCTAssertEqual(plan?.attributes.activityID, "2")
        XCTAssertEqual(plan?.attributes.activityTitle, "데브 데이")
        XCTAssertEqual(plan?.entries.map(\.scheduleID), ["10"])
        XCTAssertEqual(plan?.entries.first?.title, "세션 2")
        XCTAssertEqual(plan?.analytics["activity_id"], "2")
        XCTAssertEqual(plan?.entries.first?.trackPosition, 0, "the track's color comes along")
    }

    // MARK: 찜 없음 · 오늘 아님

    func testPlanIsNilWithoutTodayActivityOrFavoritesOfIt() {
        let today = applied("1", "우아콘 2026", block: at(10, 0)...at(18, 0))
        let soon = applied("3", "이틀 뒤 행사", block: at(10, 0, day: 29)...at(18, 0, day: 29))
        let now = at(11, 0)
        XCTAssertNil(Plan.today([], favorites: [], now: now))
        XCTAssertNil(Plan.today([today], favorites: [], now: now))
        XCTAssertNil(Plan.today([today], favorites: [favorite(1, activity: "3", at(13, 0)...at(13, 50))], now: now), "another activity's favorite")
        XCTAssertNil(Plan.today([today], favorites: [favorite(1, activity: "1", at(13, 0, day: 28)...at(13, 50, day: 28))], now: now), "tomorrow's favorite")
        XCTAssertNil(Plan.today([soon], favorites: [favorite(1, activity: "3", at(13, 0, day: 29)...at(13, 50, day: 29))], now: now), "upcoming, not today")
    }

    // MARK: 기간 (안내 시작부터 마지막 찜 끝까지, 2026-10-27 행사 안내)

    func testShowsFromStartUntilLastEndWithTheFirstSessionToWalkTo() {
        let plan = plan([(1, at(13, 0)...at(13, 50)), (2, at(14, 50)...at(15, 30))])
        let early = LiveSessionModel.moment(plan, now: at(9, 0))
        XCTAssertEqual(early.state?.current.scheduleID, "1", "started hours before: 이동 to the first session")
        XCTAssertEqual(early.state?.running, false)
        XCTAssertEqual(early.state?.minutes, 240, "이동: whole minutes to the start, past an hour too")
        XCTAssertEqual(early.boundary, at(9, 1), "the next minute boundary")
        XCTAssertTrue(plan.open(at: at(15, 29)))
        XCTAssertFalse(plan.open(at: at(15, 30)), "no 행사 안내 button once the last session ended")
        let lastSecond = LiveSessionModel.moment(plan, now: at(15, 29))
        XCTAssertEqual(lastSecond.state?.current.scheduleID, "2")
        XCTAssertNil(lastSecond.state?.next)
        XCTAssertEqual(LiveSessionModel.moment(plan, now: at(15, 30)), .init(state: nil, boundary: nil), "down at the last end")
        XCTAssertEqual(LiveSessionModel.moment(plan, now: at(16, 0)), .init(state: nil, boundary: nil))
    }

    // MARK: 현재 · 다음

    func testCurrentIsFirstUnfinishedAndNextStartsAfterIt() {
        let plan = plan([(1, at(13, 0)...at(13, 50)), (2, at(14, 50)...at(15, 30)), (3, at(16, 0)...at(16, 30))])
        let running = LiveSessionModel.moment(plan, now: at(13, 10))
        XCTAssertEqual(running.state?.current.scheduleID, "1")
        XCTAssertEqual(running.state?.running, true)
        XCTAssertEqual(running.state?.next?.scheduleID, "2")
        XCTAssertEqual(running.state.map { [$0.position, $0.total] }, [0, 3], "진행 트랙: first of three")
        let waiting = LiveSessionModel.moment(plan, now: at(14, 0))
        XCTAssertEqual(waiting.state?.current.scheduleID, "2", "between sessions the next one is current")
        XCTAssertEqual(waiting.state?.running, false)
        XCTAssertEqual(waiting.state?.next?.scheduleID, "3")
        XCTAssertEqual(waiting.state?.position, 1, "in a gap the session walked to is the current segment")
        let last = LiveSessionModel.moment(plan, now: at(16, 10))
        XCTAssertEqual(last.state?.current.scheduleID, "3")
        XCTAssertNil(last.state?.next, "nothing after the last session")
    }

    // MARK: 같은 시작 시각

    func testSameStartFavoritesKeepIDOrderAndNextStartsLater() {
        let applied = applied("1", "우아콘 2026", block: at(10, 0)...at(18, 0))
        let favorites = [favorite(7, activity: "1", at(13, 0)...at(13, 50)), favorite(3, activity: "1", at(13, 0)...at(13, 50)),
                         favorite(5, activity: "1", at(14, 50)...at(15, 30))]
        guard let plan = Plan.today([applied], favorites: favorites, now: at(11, 0)) else { return XCTFail("no plan") }
        XCTAssertEqual(plan.entries.map(\.title), ["세션 3", "세션 7", "세션 5"], "same start: id order")
        let moment = LiveSessionModel.moment(plan, now: at(13, 10))
        XCTAssertEqual(moment.state?.current.title, "세션 3")
        XCTAssertEqual(moment.state?.next?.title, "세션 5", "a session starting at the same time is not `next`")
    }

    // MARK: 다음 갱신 시각

    func testBoundaryIsTheNearestStartOrEndAhead() {
        let plan = plan([(1, at(13, 0)...at(14, 0)), (2, at(13, 30)...at(13, 45)), (3, at(14, 50)...at(15, 30))])
        XCTAssertEqual(LiveSessionModel.moment(plan, now: at(13, 10)).boundary, at(13, 30), "another session's start before the current one's end")
        XCTAssertEqual(LiveSessionModel.moment(plan, now: at(13, 40)).boundary, at(13, 45))
        XCTAssertEqual(LiveSessionModel.moment(plan, now: at(13, 50)).boundary, at(14, 0))
        let walking = LiveSessionModel.moment(plan, now: at(14, 10).addingTimeInterval(30))
        XCTAssertEqual(walking.state?.minutes, 40, "39분 30초 → 40분")
        XCTAssertEqual(walking.boundary, at(14, 11), "이동 moves on each minute")
        XCTAssertEqual(LiveSessionModel.moment(plan, now: at(14, 49).addingTimeInterval(50)).state?.minutes, 1, "under a minute reads 1분")
        XCTAssertEqual(LiveSessionModel.moment(plan, now: at(15, 0)).boundary, at(15, 30))
    }

    // MARK: 안내 꺼짐 · 로그아웃

    func testNothingShowsUnlessTheGuideRunsWithASavedPlan() {
        let plan = plan([(1, at(13, 0)...at(13, 50))])
        XCTAssertNotNil(LiveSessionModel.moment(guiding: true, plan: plan, now: at(13, 10))?.state)
        XCTAssertNil(LiveSessionModel.moment(guiding: false, plan: plan, now: at(13, 10)), "not started (or 안내 종료): nothing on its own")
        XCTAssertNil(LiveSessionModel.moment(guiding: true, plan: nil, now: at(13, 10)), "signed out: the plan was cleared")
    }

    // MARK: Fixtures

    private static let seoul: Calendar = {
        var calendar = Calendar(identifier: .gregorian)
        calendar.timeZone = TimeZone(identifier: "Asia/Seoul")!
        return calendar
    }()
    /// Seoul 2026-10-`day` `hour`:`minute`.
    private func at(_ hour: Int, _ minute: Int, day: Int = 27) -> Date {
        Self.seoul.date(from: DateComponents(year: 2026, month: 10, day: day, hour: hour, minute: minute))!
    }
    /// An applied activity whose only stage is a timed 활동 일정 block (schedule id `10`) with a 시간표.
    private func applied(_ id: String, _ title: String, block: ClosedRange<Date>) -> AppliedActivity {
        let stage = ActivityStageModel(id: "10", title: "행사", kind: .event, start: block.lowerBound, end: block.upperBound, allDay: false,
                                       dateLabel: "", timeZone: "Asia/Seoul", hasTimetable: true, phase: .activity)
        let activity = ActivityModel(id: id, title: title, summary: "", organization: "주최", program: nil, recruitmentStartAt: nil,
                                     recruitmentEndAt: nil, recruitmentStartDate: nil, recruitmentEndDateExclusive: nil, applicationStage: nil,
                                     stages: [], activityStage: stage, officialUrl: "", imageUrl: nil)
        return AppliedActivity(activity: activity, progress: ActivityStages.build(activity, marks: [:], now: at(11, 0)))
    }
    /// A favorited session `세션 {id}` of block `10` on 트랙 A.
    private func favorite(_ id: Int64, activity: String, _ time: ClosedRange<Date>) -> FavoriteSession {
        let session = TimetableSession(id: id, trackID: 1, kind: .talk, title: "세션 \(id)", description: nil, start: time.lowerBound,
                                       end: time.upperBound, speakers: [], tags: [])
        return FavoriteSession(activityID: activity, activityName: "행사", scheduleID: "10", scheduleTitle: "행사", session: session,
                               track: TimetableTrack(id: 1, name: "트랙 A", room: "그랜드볼룸 A", position: 0))
    }
    /// A plan of entries `(scheduleID, time)` in the given order, as `Plan.today` would build it from start-sorted favorites.
    private func plan(_ entries: [(Int, ClosedRange<Date>)]) -> Plan {
        Plan(attributes: LiveSessionAttributes(activityID: "1", activityTitle: "우아콘 2026", zone: "Asia/Seoul"),
             entries: entries.map {
                 .init(scheduleID: "\($0.0)", title: "세션 \($0.0)", track: "트랙 A", room: nil, start: $0.1.lowerBound, end: $0.1.upperBound)
             }, analytics: [:])
    }
}
