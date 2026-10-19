import Foundation

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
    /// Timed schedules only; date-only schedules stay in their stage's `dateLabel`.
    let schedules: [ActivityScheduleModel]
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
    /// What a participant gains (contract "태그"); empty when none or on older responses.
    var tags: [Tag] = []
    /// Free-text place (collected, or the curated place's name; contract "활동 장소"); nil when unknown.
    var location: String? = nil
    /// The curated place with coordinates (contract "활동 장소"); nil when none.
    var place: ActivityPlace? = nil

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
    /// The activity period as text (contract "일정 표시 우선순위 — 2026-10-19 밤"): the curated structured dates first, else the collected
    /// `dateLabel`; nil when neither exists so each screen keeps its own `미정`/`일정 미확인`.
    func scheduleText(at now: Date) -> String? { activityStage?.text(now: now) }
    /// The recruitment window as text by the same rule; nil without a window or any text for it.
    func recruitmentText(at now: Date) -> String? { applicationStage?.text(now: now) }
    /// `yyyy-MM-dd` as Seoul midnight; nil when absent or unreadable.
    static func seoulDay(_ raw: String?) -> Date? {
        guard let raw else { return nil }
        let formatter = DateFormatter()
        formatter.calendar = Calendar(identifier: .gregorian)
        formatter.locale = Locale(identifier: "en_US_POSIX")
        formatter.timeZone = TimeZone(identifier: "Asia/Seoul")
        formatter.dateFormat = "yyyy-MM-dd"
        return formatter.date(from: raw)
    }
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
        let precise = ISO8601DateFormatter()
        precise.formatOptions = [.withInternetDateTime, .withFractionalSeconds]
        return precise.date(from: raw) ?? ISO8601DateFormatter().date(from: raw)
    }
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
    let id: String
    let title: String
    let kind: Kind
    let start: Date?
    let end: Date?
    let allDay: Bool
    let dateLabel: String
    let timeZone: String
    /// The day a stage counts from: its start, or its end when only that is known (ordering, D-day, `지난 활동`).
    var anchor: Date? { start ?? end }
    /// The overlap check (`CalendarConflictView`) needs a non-empty interval in a known zone.
    var schedule: ActivityScheduleModel? {
        guard let start, let end, start < end else { return nil }
        return ActivityScheduleModel(id: id, title: title, start: start, end: end, dateLabel: dateLabel, timeZone: timeZone)
    }
    /// The stage as text (contract "일정 표시 우선순위 — 2026-10-19 밤"): the structured bounds when there are any, else the collected
    /// `dateLabel`, else nil. Timed: `M월 d일 (요일) HH:mm` in the stage's zone; a range is `A ~ B` (`A ~ HH:mm` on one day), an end alone
    /// `~ B 마감`. All-day: `M월 d일 (요일)` by Seoul day with the inclusive last day. Both bounds carry the year when either falls outside
    /// `now`'s Seoul year or they span years (web rule).
    func text(now: Date) -> String? {
        guard start != nil || end != nil else { return dateLabel.isEmpty ? nil : dateLabel }
        let zone = allDay ? ActivityText.seoul : TimeZone(identifier: timeZone) ?? ActivityText.seoul
        var calendar = Calendar(identifier: .gregorian)
        calendar.timeZone = zone
        // All-day `end` is the exclusive midnight; the last day is the one before it.
        let last = allDay ? end.flatMap { calendar.date(byAdding: .day, value: -1, to: $0) } : end
        let years = Set([start, last].compactMap { $0 }.map { calendar.component(.year, from: $0) })
        var seoulCalendar = Calendar(identifier: .gregorian)
        seoulCalendar.timeZone = ActivityText.seoul
        let withYear = years.count > 1 || years != [seoulCalendar.component(.year, from: now)]
        let pattern = (withYear ? "yyyy년 M월 d일 (E)" : "M월 d일 (E)") + (allDay ? "" : " HH:mm")
        guard let start else { return last.map { "~ " + ActivityText.format($0, pattern, zone: zone) + " 마감" } }
        let first = ActivityText.format(start, pattern, zone: zone)
        guard let last, last > start else { return first }
        if !allDay, calendar.isDate(start, inSameDayAs: last) { return first + " ~ " + ActivityText.format(last, "HH:mm", zone: zone) }
        return first + " ~ " + ActivityText.format(last, pattern, zone: zone)
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
        let formatter = DateFormatter()
        formatter.calendar = Calendar(identifier: .gregorian)
        formatter.locale = Locale(identifier: "ko_KR")
        formatter.timeZone = zone
        formatter.dateFormat = pattern
        return formatter.string(from: date)
    }
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
    /// Open activities whose name matched `q`; such programs start expanded.
    let matchedActivityIDs: [String]
    /// Raw ISO 8601 of the program's most recent published activity start (contract "모집 중인 프로그램만 보기"); nil when unknown.
    var latestActivityAt: String? = nil
    /// Activities whose recruitment (or activity, without a window) has not started yet (contract "마감 숨기기" 2026-10-19 밤); 0 when unknown.
    var upcomingActivityCount: Int = 0
    /// Raw ISO 8601 of the earliest recruitment start among the upcoming ones (activity start without a window); nil when none.
    var nearestRecruitmentStartAt: String? = nil
    /// Program tags, or the union of its open activities' tags (contract "태그": at most 6, vocabulary order).
    var tags: [Tag] = []
    /// The program logo (contract "프로그램 로고" 2026-10-19 밤), already resolved against the API origin; nil without a logo.
    var logoURL: URL? = nil
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
