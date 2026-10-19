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
    /// `activitySchedule.dateLabel`; empty when unknown.
    let dateLabel: String
    /// Timed schedules only; date-only schedules stay in `dateLabel`.
    let schedules: [ActivityScheduleModel]
    /// True when the API sent an `application` schedule, even a date-less one (contract "내 활동 진행 트랙": a `지원` stage).
    let hasApplication: Bool
    /// Every `schedules[]` entry (timed, all-day or date-less) with its kind, in API order: the progress track's middle stages.
    let stages: [ActivityStageModel]
    /// `activitySchedule` as the track's last stage (contract "내 활동 진행 트랙": `활동`, or its title); nil only if the API omitted it.
    let activityStage: ActivityStageModel?
    let officialUrl: String
    let imageUrl: String?
    /// Who may apply (contract "대상자 조건"); nil when the activity has none or the JSON was unreadable.
    var eligibility: Eligibility? = nil
    /// 활동 종류 (contract "활동 종류"); nil for an unknown code or none (미분류).
    var category: ActivityCategory? = nil
    /// Ordering key inside a category group (contract "활동 종류"): the application's end instant, or the exclusive end date
    /// at Seoul midnight when the window is date-only. When no usable deadline exists (no window, or a window without an
    /// end) the activity start (instant, or start date at Seoul midnight) stands in; nil when neither exists.
    var recruitmentDeadline: Date? = nil

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
    /// The official link (https only); applying goes through it only while recruiting.
    var officialURL: URL? { Self.safeURL(officialUrl) }
    func applicationURL(at now: Date) -> URL? { recruitment(at: now) == .open ? officialURL : nil }
    var imageURL: URL? { Self.safeURL(imageUrl) }
    var recruitmentEnd: Date? { Self.instant(recruitmentEndAt) }

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
struct ActivityScheduleModel: Equatable, Identifiable, Sendable {
    let id: String
    let title: String
    let start: Date
    let end: Date
    let dateLabel: String
    let timeZone: String
}
/// One stage of the progress track (contract "내 활동 진행 트랙 — 2026-10-18"). Timed stages carry `start`/`end` instants;
/// all-day stages carry Seoul midnight of `startDate` and of `endDateExclusive`; date-less stages have neither and only `dateLabel`.
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
    /// The overlap check (`CalendarConflictView`) needs a non-empty interval in a known zone.
    var schedule: ActivityScheduleModel? {
        guard let start, let end, start < end else { return nil }
        return ActivityScheduleModel(id: id, title: title, start: start, end: end, dateLabel: dateLabel, timeZone: timeZone)
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
    var nearestRecruitmentEnd: Date? { ActivityModel.instant(nearestRecruitmentEndAt) }
    var latestActivity: Date? { ActivityModel.instant(latestActivityAt) }
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
