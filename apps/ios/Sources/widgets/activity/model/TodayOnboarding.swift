import Foundation
import Observation

/// One row of the 오늘 행사 온보딩 sheet (contract "오늘 행사 온보딩" 2026-10-27): an event with a 활동 일정 block today.
struct TodayCandidate: Identifiable, Equatable {
    let activity: ActivityModel
    /// Today's 활동 일정 blocks in start order (never empty).
    let blocks: [ActivityStageModel]
    var id: String { activity.id }
    /// `place.name`, else the free-text location; nil with neither.
    var place: String? { activity.place?.name ?? activity.location }
    /// Earliest start ~ latest end of today's timed blocks in the first one's zone (`10:00–17:30`); `종일` without a timed block.
    var timeRange: String {
        let timed = blocks.filter { !$0.allDay && $0.start != nil }
        guard let first = timed.first, let start = timed.compactMap(\.start).min() else { return "종일" }
        let end = timed.compactMap(\.end).max()
        return ActivityText.time(start, zone: first.timeZone) + (end.map { "–" + ActivityText.time($0, zone: first.timeZone) } ?? "")
    }
    /// How the row reads: `우아콘 2026, 그랜드 인터컨티넨탈 서울 파르나스, 오늘 10:00–17:30`.
    var spokenLabel: String { [activity.title, place, "오늘 " + timeRange].compactMap { $0 }.joined(separator: ", ") }

    /// The sheet's rows: `activities` with a 활동 일정 block on `now`'s Seoul day (`TodayEvent.todayBlocks`, the 오늘 내 행사 judgment), those
    /// in 내 활동 left out, earliest first block first, at most five.
    static func pick(_ activities: [ActivityModel], applied: Set<String>, now: Date) -> [TodayCandidate] {
        let rows = activities.filter { !applied.contains($0.id) }.compactMap { activity in
            let blocks = TodayEvent.todayBlocks(of: activity, now: now)
            return blocks.isEmpty ? nil : TodayCandidate(activity: activity, blocks: blocks)
        }
        return Array(rows.sorted { ($0.blocks[0].start ?? .distantFuture, $0.id) < ($1.blocks[0].start ?? .distantFuture, $1.id) }.prefix(5))
    }
}

/// 오늘 행사 온보딩 (contract "오늘 행사 온보딩" 2026-10-27): on opening the app, asks once per Seoul day whether the user takes part in an event
/// running today. `check` reads the events (`GET /v1/activities?category=…&status=…&size=50`, no token) and keeps the rows as `pending`; the
/// app moves them to `prompt` once nothing else is on screen (`present`). The day is remembered on the device once the read succeeds.
@MainActor @Observable final class TodayOnboardingModel {
    /// The sheet's content while it is up.
    struct Prompt: Identifiable {
        let day: String
        let candidates: [TodayCandidate]
        var id: String { day }
    }
    /// Rows found and not yet shown (the app waits for the intro, sign-in, consent, funnel and queue to close).
    private(set) var pending: [TodayCandidate]?
    /// Bound to the sheet; set to nil by an answer or by pulling the sheet down (`dismissed`).
    var prompt: Prompt?
    @ObservationIgnored private var checking = false
    @ObservationIgnored private var answered = false
    private let fetch: @Sendable () async throws -> [ActivityModel]
    /// The program logo URL for a `programID`.
    let logoURL: @Sendable (Int64) -> URL
    private static let shownDayKey = "dearby.todayOnboarding.shownDay"
    init(fetch: @escaping @Sendable () async throws -> [ActivityModel], logoURL: @escaping @Sendable (Int64) -> URL) {
        self.fetch = fetch
        self.logoURL = logoURL
    }
    /// "Now" for the judgment. Debug: `DEARBY_FAKE_TODAY=yyyy-MM-dd` stands for noon (Seoul) of that day and the day is not remembered, so
    /// the sheet can be checked when the server has no event today.
    var now: Date {
        #if DEBUG
        if let fake = Self.fakeToday { return fake }
        #endif
        return Date()
    }
    #if DEBUG
    private static let fakeToday: Date? = ProcessInfo.processInfo.environment["DEARBY_FAKE_TODAY"].flatMap { day in
        ISO8601DateFormatter().date(from: day + "T12:00:00+09:00")
    }
    #endif
    private var remembers: Bool {
        #if DEBUG
        return Self.fakeToday == nil
        #else
        return true
        #endif
    }
    private var today: String { ActivityText.format(now, "yyyyMMdd", zone: ActivityText.seoul) }
    /// Reads today's events unless they were read today, one check at a time; `applied` is 내 활동 as it stands now (complete: the app
    /// calls this signed out, or signed in once the server's 내 활동 is read). A failed read asks nothing.
    func check(applied: Set<String>) async {
        guard !checking, prompt == nil, !(remembers && UserDefaults.standard.string(forKey: Self.shownDayKey) == today) else { return }
        checking = true
        defer { checking = false }
        guard let activities = try? await fetch() else { return }
        // Remembered on a successful read, so a day without events does not ask the server on every return.
        if remembers { UserDefaults.standard.set(today, forKey: Self.shownDayKey) }
        let rows = TodayCandidate.pick(activities, applied: applied, now: now)
        pending = rows.isEmpty ? nil : rows
    }
    /// The app found the screen clear: the pending rows go up (the day was remembered when they were read).
    /// Rows that joined 내 활동 meanwhile (`applied`) drop out; none left asks nothing.
    func present(applied: Set<String>) {
        guard let rows = pending?.filter({ !applied.contains($0.id) }), prompt == nil else { return }
        pending = nil
        guard !rows.isEmpty else { return }
        answered = false
        prompt = Prompt(day: today, candidates: rows)
        AppAnalytics.event("today_onboarding_shown", ["count": rows.count])
    }
    /// `참여해요` or `오늘은 아니에요` (사용 통계 `today_onboarding_answer`, `count` = chosen rows).
    func answer(joined: [TodayCandidate]?) {
        answered = true
        AppAnalytics.event("today_onboarding_answer", ["result": joined == nil ? "skip" : "joined", "count": joined?.count ?? 0])
        prompt = nil
    }
    /// The sheet is gone; pulled down without an answer counts as `skip`.
    func dismissed() {
        if !answered { AppAnalytics.event("today_onboarding_answer", ["result": "skip", "count": 0]) }
        answered = true
        prompt = nil
    }
}
