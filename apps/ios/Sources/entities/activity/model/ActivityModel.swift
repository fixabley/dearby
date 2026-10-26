import Foundation
import Synchronization

/// One activity from `GET /v1/activities` (contract "활동 조회 — 2026-10-14"). Missing values stay nil and read as unconfirmed.
struct ActivityModel: Equatable, Identifiable, Sendable {
    enum Recruitment: Equatable, Sendable { case upcoming, open, closed, unknown }
    /// The API's numeric id as a string: navigation uses string ids.
    let id: String
    let title: String
    let summary: String
    let organization: String
    /// `program.name`; nil when the API sent none.
    let program: String?
    // The recruitment window from `application`. Raw ISO 8601: a present but unreadable time must not count as absent.
    let recruitmentStartAt: String?
    let recruitmentEndAt: String?
    /// The same window when it is date-only (`yyyy-MM-dd`, end exclusive): judged by Seoul days.
    let recruitmentStartDate: String?
    let recruitmentEndDateExclusive: String?
    /// The `application` schedule with its bounds and collected text, even a date-less one (contract "내 활동 진행 트랙": a `지원` stage);
    /// nil when the API sent no window.
    let applicationStage: ActivityStageModel?
    /// Every `schedules[]` entry (timed, all-day or date-less) with its kind, in API order: the progress track's middle stages.
    let stages: [ActivityStageModel]
    /// `activitySchedule` as the track's last stage (contract "내 활동 진행 트랙": `활동`, or its title); nil only if the API omitted it.
    let activityStage: ActivityStageModel?
    let officialUrl: String
    let imageUrl: String?
    /// The curated application link (contract "신청 확인 조건 완화·공개 applicationUrl" 2026-10-19 밤); nil when none or on older responses.
    var applicationUrl: String? = nil
    /// Who may apply (contract "대상자 조건"); nil when the activity has none or the JSON was unreadable.
    var eligibility: Eligibility? = nil
    /// 활동 종류 (contract "활동 종류"); nil for an unknown code or none (미분류).
    var category: ActivityCategory? = nil
    /// Ordering key inside a category group (contract "활동 종류"): the application's end instant, or the exclusive end date
    /// at Seoul midnight when the window is date-only. When no usable deadline exists (no window, or a window without an
    /// end) the activity start (instant, or start date at Seoul midnight) stands in; nil when neither exists.
    var recruitmentDeadline: Date? = nil
    /// Every 분야 tag in the server's relevance order (contract "분야·성격 태그와 아이콘": `fieldTagsAll`); empty when none or on older responses.
    var fieldTags: [Tag] = []
    /// What a participant gains (성격 tags, vocabulary order); older responses' `tags` stand in.
    var traitTags: [Tag] = []
    /// Free-text place (collected, or the curated place's name; contract "활동 장소"); nil when unknown.
    var location: String? = nil
    /// The curated place with coordinates (contract "활동 장소"); nil when none.
    var place: ActivityPlace? = nil
    /// 한 줄 소개 and 핵심 세 줄 (contract "맞춤 대기열" 2026-10-24); nil/empty when none or on older responses.
    var hook: String? = nil
    var highlights: [ActivityHighlight] = []

    /// Recruitment only from the application window against now (contract "열린 활동 판정 일원화", 2026-10-19). A timed window
    /// compares instants and a missing bound is open on that side; a date-only window compares Seoul days
    /// (`startDate ≤ today < endDateExclusive`). No window at all, or an unreadable bound, needs checking.
    func recruitment(at now: Date) -> Recruitment {
        if recruitmentStartAt != nil || recruitmentEndAt != nil {
            let start = Self.instant(recruitmentStartAt), end = Self.instant(recruitmentEndAt)
            guard (start == nil) == (recruitmentStartAt == nil), (end == nil) == (recruitmentEndAt == nil) else { return .unknown }
            if let start, now < start { return .upcoming }
            if let end, now >= end { return .closed }
            return .open
        }
        if recruitmentStartDate != nil || recruitmentEndDateExclusive != nil {
            let start = Self.seoulDay(recruitmentStartDate), end = Self.seoulDay(recruitmentEndDateExclusive)
            guard (start == nil) == (recruitmentStartDate == nil), (end == nil) == (recruitmentEndDateExclusive == nil) else { return .unknown }
            if let start, now < start { return .upcoming }
            if let end, now >= end { return .closed }
            return .open
        }
        return .unknown
    }
    /// When this activity's recruitment opens: the window's start (instant, or start date at Seoul midnight); with no
    /// window at all, the activity start. nil when neither is known. Used for the `모집 예정` group (contract "모집 중인 프로그램만 보기").
    var upcomingStart: Date? {
        if recruitmentStartAt != nil || recruitmentEndAt != nil || recruitmentStartDate != nil || recruitmentEndDateExclusive != nil {
            return Self.instant(recruitmentStartAt) ?? Self.seoulDay(recruitmentStartDate)
        }
        return activityStage?.anchor
    }
    /// Some block (`schedules[]` or the activity period) has a 트랙·세션 시간표.
    var hasTimetable: Bool { (stages + [activityStage].compactMap { $0 }).contains(where: \.hasTimetable) }
    /// Everything the overlap check can compare (contract "겹침 확인 시간" 2026-10-22): `schedules[]` and the activity period, each timed
    /// or date-only with an admin time window. The recruitment window is not an event and is left out. Empty: the detail says it cannot check.
    var overlapSchedules: [ActivityScheduleModel] { (stages + [activityStage].compactMap { $0 }).flatMap(\.overlapSchedules) }
    /// The activity period as text (contract "일정 표시 우선순위 — 2026-10-19 밤"): the curated structured dates first, else the collected
    /// `dateLabel`; nil when neither exists so each screen keeps its own `미정`/`일정 미확인`.
    func scheduleText(at now: Date) -> String? { activityStage?.text(now: now) }
    /// The recruitment window as text by the same rule (`~ … 마감` when only the deadline is known); nil without a window or any text for it.
    func recruitmentText(at now: Date) -> String? { applicationStage?.text(now: now, recruitment: true) }
    /// `yyyy-MM-dd` as Seoul midnight; nil when absent or unreadable.
    static func seoulDay(_ raw: String?) -> Date? {
        raw.flatMap(seoulDayFormatter.date(from:))
    }
    // Formatters are built once: cards re-read their dates on every render, and building one per call kept the main thread busy
    // long enough for the watchdog to kill the app on expand/collapse and in the background (crash logs 2026-10-22, 0x8BADF00D).
    private static let seoulDayFormatter: DateFormatter = {
        let formatter = DateFormatter()
        formatter.calendar = Calendar(identifier: .gregorian)
        formatter.locale = Locale(identifier: "en_US_POSIX")
        formatter.timeZone = TimeZone(identifier: "Asia/Seoul")
        formatter.dateFormat = "yyyy-MM-dd"
        return formatter
    }()
    func statusLabel(at now: Date) -> String {
        switch recruitment(at: now) {
        case .upcoming: "모집 예정"
        case .open: "모집 중"
        case .closed: "모집 마감"
        case .unknown: "모집 여부 확인 필요"
        }
    }
    /// The official link (https only).
    var officialURL: URL? { Self.safeURL(officialUrl) }
    /// The activity's page on the build's web origin, written into events added from the app and used to leave them out of the
    /// overlap check (contract "반복 일정과 겹침 결과 묶음" 2026-10-22).
    static func calendarLink(_ id: String, web: URL) -> URL { web.appending(path: "activities/\(id)") }
    /// The curated application link (https only); the detail CTA opens it before the official notice.
    var applicationSiteURL: URL? { Self.safeURL(applicationUrl) }
    /// Applying is over (contract "신청 확인 조건 완화"): the recruitment deadline passed, or, with no window to judge by, the activity
    /// itself ended. Open, upcoming and unknown-but-not-ended all still lead to the "신청하셨나요?" prompt.
    func applicationClosed(at now: Date) -> Bool {
        switch recruitment(at: now) {
        case .closed: true
        case .open, .upcoming: false
        case .unknown: activityStage.flatMap { $0.end ?? $0.start }.map { $0 < now } ?? false
        }
    }
    var imageURL: URL? { Self.safeURL(imageUrl) }

    static func safeURL(_ raw: String?) -> URL? {
        guard let raw, let url = URL(string: raw), url.scheme?.lowercased() == "https",
              let host = url.host, !host.isEmpty, url.user == nil, url.password == nil else { return nil }
        return url
    }
    static func instant(_ raw: String?) -> Date? {
        guard let raw else { return nil }
        return preciseInstant.date(from: raw) ?? plainInstant.date(from: raw)
    }
    // ISO8601DateFormatter is thread-safe (Apple docs) but not marked Sendable.
    private nonisolated(unsafe) static let preciseInstant: ISO8601DateFormatter = {
        let formatter = ISO8601DateFormatter()
        formatter.formatOptions = [.withInternetDateTime, .withFractionalSeconds]
        return formatter
    }()
    private nonisolated(unsafe) static let plainInstant = ISO8601DateFormatter()
}
/// One 핵심 줄 (`highlights[]`, contract "맞춤 대기열" 2026-10-24): an icon from the contract's set (nil for an unknown one) and a short text.
struct ActivityHighlight: Equatable, Sendable {
    enum Icon: String, Sendable { case cost, period, team, mentor, award, place, talk, ticket }
    let icon: Icon?
    let text: String
}
/// A curated place (contract "활동 장소 — 2026-10-19"): name, road address and coordinates, all present together.
struct ActivityPlace: Equatable, Sendable {
    let name: String
    let address: String
    let lat: Double
    let lng: Double
    /// Apple Maps with the name as the query and the coordinates as the pin (contract: iOS `maps://?q=`).
    var mapsURL: URL? {
        var components = URLComponents()
        components.scheme = "maps"
        components.host = ""
        components.queryItems = [URLQueryItem(name: "q", value: name), URLQueryItem(name: "ll", value: "\(lat),\(lng)")]
        return components.url
    }
}
struct ActivityScheduleModel: Equatable, Identifiable, Sendable {
    let id: String
    let title: String
    let start: Date
    let end: Date
    let dateLabel: String
    let timeZone: String
}
/// One stage of the progress track (contract "내 활동 진행 트랙 — 2026-10-18"). Timed stages carry `start`/`end` instants, either of
/// which may be missing; all-day stages carry Seoul midnight of `startDate` and of `endDateExclusive` (`allDay`), again either may be
/// missing; date-less stages have neither and only `dateLabel`.
struct ActivityStageModel: Equatable, Identifiable, Sendable {
    enum Kind: String, Sendable { case deadline = "DEADLINE", announcement = "ANNOUNCEMENT", interview = "INTERVIEW", event = "EVENT" }
    /// The schedule's numeric id as a string (the server's `scheduleId`).
    let id: String
    let title: String
    let kind: Kind
    let start: Date?
    let end: Date?
    let allDay: Bool
    let dateLabel: String
    let timeZone: String
    /// What the user may pick for this stage (contract "내 활동 서버 저장·일정 상태" 2026-10-22); the default three when the API sent none.
    var statuses: [ScheduleStatus] = ScheduleStatus.defaults
    /// The admin's Seoul time window for comparing a date-only schedule with the device calendar; nil when none.
    var overlapCheck: OverlapCheck? = nil
    /// The admin's repeat rule (contract "반복 일정" 2026-10-22); nil when the schedule does not repeat or the rule is unreadable.
    var recurrence: ScheduleRecurrence? = nil
    /// The block has a track·session timetable (contract "트랙·세션 시간표" 2026-10-26: `Schedule.hasTimetable`); false on older responses.
    var hasTimetable = false
    /// The day a stage counts from: its start, or its end when only that is known (ordering, D-day, `지난 활동`).
    var anchor: Date? { start ?? end }
    /// What the overlap check (`CalendarConflictView`) compares (contract "겹침 확인 시간", "반복 일정" 2026-10-22). With a repeat rule,
    /// one window per occurrence (at most 400): a timed stage's own interval moved by whole Seoul days, a date-only stage's
    /// `overlapCheck` window on each occurrence day from `startDate`. Without one: a timed stage's own non-empty interval; a date-only
    /// stage with `overlapCheck`, that Seoul window on each of its days (at most 62). Otherwise nothing.
    var overlapSchedules: [ActivityScheduleModel] {
        func window(_ index: Int, _ start: Date, _ end: Date, zone: String) -> ActivityScheduleModel {
            ActivityScheduleModel(id: index == 0 && recurrence == nil && !allDay ? id : "\(id)|\(index)", title: title, start: start,
                                  end: end, dateLabel: dateLabel, timeZone: zone)
        }
        let seoul = Self.seoul
        if !allDay {
            guard let start, let end, start < end else { return [] }
            guard let recurrence else { return [window(0, start, end, zone: timeZone)] }
            return recurrence.dayOffsets(from: start).enumerated().compactMap { index, offset in
                guard let from = seoul.date(byAdding: .day, value: offset, to: start),
                      let to = seoul.date(byAdding: .day, value: offset, to: end) else { return nil }
                return window(index, from, to, zone: timeZone)
            }
        }
        guard let overlapCheck, let first = start ?? end.flatMap({ seoul.date(byAdding: .day, value: -1, to: $0) }) else { return [] }
        let days: [Date]
        if let recurrence {
            days = recurrence.dayOffsets(from: first).compactMap { seoul.date(byAdding: .day, value: $0, to: first) }
        } else {
            let stop = end ?? seoul.date(byAdding: .day, value: 1, to: first) ?? first
            days = (0..<62).compactMap { seoul.date(byAdding: .day, value: $0, to: first) }.filter { $0 < stop }
        }
        return days.compactMap { overlapCheck.interval(on: $0) }.enumerated().map { window($0, $1.start, $1.end, zone: "Asia/Seoul") }
    }
    private static var seoul: Calendar {
        var calendar = Calendar(identifier: .gregorian)
        calendar.timeZone = ActivityText.seoul
        return calendar
    }
    /// The stage as text (contract "일정 표시 우선순위 — 2026-10-19 밤"): the structured bounds when there are any, else the collected
    /// `dateLabel`, else nil. Timed: `M월 d일 (요일) HH:mm` in the stage's zone; a range is `A ~ B` (`A ~ HH:mm` on one day). All-day:
    /// `M월 d일 (요일)` by Seoul day with the inclusive last day. Only a date outside `now`'s Seoul year carries its year
    /// (`12월 30일 (수) ~ 2027년 1월 2일 (토)`). An end alone reads `~ B 마감` for a `recruitment` window, `~ B까지` otherwise.
    func text(now: Date, recruitment: Bool = false) -> String? {
        guard start != nil || end != nil else { return dateLabel.isEmpty ? nil : dateLabel }
        let zone = allDay ? ActivityText.seoul : TimeZone(identifier: timeZone) ?? ActivityText.seoul
        var calendar = Calendar(identifier: .gregorian)
        calendar.timeZone = zone
        var seoulCalendar = Calendar(identifier: .gregorian)
        seoulCalendar.timeZone = ActivityText.seoul
        let thisYear = seoulCalendar.component(.year, from: now)
        func day(_ date: Date) -> String {
            let pattern = (calendar.component(.year, from: date) == thisYear ? "M월 d일 (E)" : "yyyy년 M월 d일 (E)") + (allDay ? "" : " HH:mm")
            return ActivityText.format(date, pattern, zone: zone)
        }
        // All-day `end` is the exclusive midnight; the last day is the one before it.
        let last = allDay ? end.flatMap { calendar.date(byAdding: .day, value: -1, to: $0) } : end
        guard let start else { return last.map { "~ " + day($0) + (recruitment ? " 마감" : "까지") } }
        guard let last, last > start else { return day(start) }
        if !allDay, calendar.isDate(start, inSameDayAs: last) { return day(start) + " ~ " + ActivityText.format(last, "HH:mm", zone: zone) }
        return day(start) + " ~ " + day(last)
    }
}
/// One status a stage can be marked with (contract "내 활동 서버 저장·일정 상태" 2026-10-22): the admin's rows carry an `id`; the
/// default three (대기·통과·탈락) the server fills in for a schedule without rows have none.
struct ScheduleStatus: Equatable, Hashable, Sendable {
    enum Kind: String, Sendable { case waiting = "WAITING", passed = "PASSED", failed = "FAILED", other = "OTHER" }
    let id: Int64?
    let name: String
    let kind: Kind
    static let defaults = [ScheduleStatus(id: nil, name: "대기", kind: .waiting), ScheduleStatus(id: nil, name: "통과", kind: .passed),
                           ScheduleStatus(id: nil, name: "탈락", kind: .failed)]
}
/// 겹침 확인 시간 (contract "겹침 확인 시간" 2026-10-22): `HH:mm` Seoul times for a date-only schedule.
struct OverlapCheck: Equatable, Sendable {
    let startTime: String
    let endTime: String
    /// The window on `day` (Seoul midnight); nil when a time is unreadable or the window is empty.
    func interval(on day: Date) -> DateInterval? {
        func at(_ time: String) -> Date? {
            let parts = time.split(separator: ":").compactMap { Int($0) }
            guard parts.count >= 2, (0...24).contains(parts[0]), (0..<60).contains(parts[1]) else { return nil }
            return day.addingTimeInterval(TimeInterval(parts[0] * 3600 + parts[1] * 60))
        }
        guard let start = at(startTime), let end = at(endTime), start < end else { return nil }
        return DateInterval(start: start, end: end)
    }
}
/// Korean date text in a schedule's zone.
enum ActivityText {
    static let seoul = TimeZone(identifier: "Asia/Seoul")!
    static func time(_ date: Date, zone: String = "Asia/Seoul") -> String { format(date, "HH:mm", zone: zone) }
    static func day(_ date: Date, zone: String) -> String { format(date, "M/d", zone: zone) }
    static func format(_ date: Date, _ pattern: String, zone: String) -> String {
        format(date, pattern, zone: TimeZone(identifier: zone) ?? seoul)
    }
    static func format(_ date: Date, _ pattern: String, zone: TimeZone) -> String {
        let key = "\(pattern)|\(zone.identifier)"
        let formatter = formatters.withLock { cache in
            if let cached = cache[key] { return cached }
            let formatter = DateFormatter()
            formatter.calendar = Calendar(identifier: .gregorian)
            formatter.locale = Locale(identifier: "ko_KR")
            formatter.timeZone = zone
            formatter.dateFormat = pattern
            cache[key] = formatter
            return formatter
        }
        return formatter.string(from: date)
    }
    /// One formatter per pattern and zone (see `ActivityModel.seoulDayFormatter`).
    private static let formatters = Mutex<[String: DateFormatter]>([:])
}
/// One page of `GET /v1/activities`. A nil `nextCursor` is the last page.
struct ActivityPage: Sendable {
    let items: [ActivityModel]
    let nextCursor: Int64?
}
/// One program with open activities from `GET /v1/discovery/programs` (contract "발견 프로그램 묶음 — 2026-10-18").
struct ProgramSummary: Equatable, Identifiable, Sendable {
    let id: Int64
    let name: String
    let nameEn: String?
    let organization: String
    /// Open activities that match the query and type filter; 0 when the list also shows programs without open activities.
    let openActivityCount: Int
    /// Raw ISO 8601 of the nearest recruitment deadline among them; nil when none has a deadline.
    let nearestRecruitmentEndAt: String?
    /// Open activities whose name matched `q`; the collapsed card says `맞는 활동 n개`.
    let matchedActivityIDs: [String]
    /// Raw ISO 8601 of the program's most recent published activity start (contract "모집 중인 프로그램만 보기"); nil when unknown.
    var latestActivityAt: String? = nil
    /// Activities whose recruitment (or activity, without a window) has not started yet (contract "마감 숨기기" 2026-10-19 밤); 0 when unknown.
    var upcomingActivityCount: Int = 0
    /// Raw ISO 8601 of the earliest recruitment start among the upcoming ones (activity start without a window); nil when none.
    var nearestRecruitmentStartAt: String? = nil
    /// `nearestRecruitmentEndAt` is an activity start (an open activity without a recruitment deadline), not a deadline; false on older responses.
    var nearestIsActivityStart = false
    /// The program's most relevant 분야 tags, at most two (contract "분야·성격 태그와 아이콘"); empty on older responses.
    var fieldTags: [Tag] = []
    /// The program's 성격 tags (vocabulary order); older responses' `tags` stand in.
    var traitTags: [Tag] = []
    /// The program logo (contract "프로그램 로고" 2026-10-19 밤), already resolved against the API origin; nil without a logo.
    var logoURL: URL? = nil
    /// An open or upcoming activity of the bundle has a 트랙·세션 시간표 (contract "트랙·세션 시간표" 2026-10-26): the collapsed card's `시간표` badge.
    var hasTimetable = false
    var nearestRecruitmentEnd: Date? { ActivityModel.instant(nearestRecruitmentEndAt) }
    var latestActivity: Date? { ActivityModel.instant(latestActivityAt) }
    var nearestRecruitmentStart: Date? { ActivityModel.instant(nearestRecruitmentStartAt) }
}
/// One page of `GET /v1/discovery/programs`. A nil `nextCursor` is the last page.
struct ProgramPage: Sendable {
    let items: [ProgramSummary]
    let nextCursor: Int64?
}

/// Nested eligibility conditions (contract "대상자 조건(eligibility) — 2026-10-18"). JSON keys are camelCase;
/// `field`/`values` are the Korean vocabulary, `fieldEn`/`valuesEn` the English twins.
struct Eligibility: Equatable, Sendable {
    enum Op: String, Sendable { case all, any }
    indirect enum Item: Equatable, Sendable {
        case group(Eligibility)
        case condition(Condition)
    }
    struct Condition: Equatable, Sendable {
        let field: String
        let fieldEn: String
        var from: Double? = nil
        var to: Double? = nil
        var values: [String] = []
        var valuesEn: [String] = []
        var note: String? = nil
    }
    let op: Op
    let items: [Item]
}
