import Foundation

/// One activity from `GET /v1/activities` (contract "활동 조회 — 2026-10-14"). Missing values stay nil and read as unconfirmed.
struct ActivityModel: Equatable, Identifiable, Sendable {
    enum Recruitment: Equatable, Sendable { case upcoming, open, closed, unknown }
    /// The API's numeric id as a string: navigation uses string ids.
    let id: String
    let title: String
    let summary: String
    let organization: String
    // The recruitment window from `application`. Raw ISO 8601: a present but unreadable time must not count as absent.
    let recruitmentStartAt: String?
    let recruitmentEndAt: String?
    /// `activitySchedule.dateLabel`; empty when unknown.
    let dateLabel: String
    /// Timed schedules only; date-only schedules stay in `dateLabel`.
    let schedules: [ActivityScheduleModel]
    let officialUrl: String
    let imageUrl: String?
    /// Who may apply (contract "대상자 조건"); nil when the activity has none or the JSON was unreadable.
    var eligibility: Eligibility? = nil

    /// Recruitment only from the application window against now. A missing bound is open on that side;
    /// no window at all, or an unreadable bound, needs checking.
    func recruitment(at now: Date) -> Recruitment {
        let start = Self.instant(recruitmentStartAt), end = Self.instant(recruitmentEndAt)
        guard start != nil || end != nil, (start == nil) == (recruitmentStartAt == nil),
              (end == nil) == (recruitmentEndAt == nil) else { return .unknown }
        if let start, now < start { return .upcoming }
        if let end, now >= end { return .closed }
        return .open
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
    /// Open activities that match the query and type filter; the API returns only programs with at least one.
    let openActivityCount: Int
    /// Raw ISO 8601 of the nearest recruitment deadline among them; nil when none has a deadline.
    let nearestRecruitmentEndAt: String?
    /// Open activities whose name matched `q`; such programs start expanded.
    let matchedActivityIDs: [String]
    var nearestRecruitmentEnd: Date? { ActivityModel.instant(nearestRecruitmentEndAt) }
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
