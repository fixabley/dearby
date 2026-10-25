import Foundation

/// A Seoul calendar day (year, month, day): the favorites calendar's key, independent of the device's zone.
struct SeoulDay: Hashable, Comparable, Sendable {
    let year: Int
    let month: Int
    let day: Int
    static let calendar: Calendar = {
        var calendar = Calendar(identifier: .gregorian)
        calendar.timeZone = ActivityText.seoul
        calendar.locale = Locale(identifier: "ko_KR")
        return calendar
    }()
    init(year: Int, month: Int, day: Int) {
        self.year = year
        self.month = month
        self.day = day
    }
    init(_ date: Date) {
        let parts = Self.calendar.dateComponents([.year, .month, .day], from: date)
        self.init(year: parts.year ?? 0, month: parts.month ?? 0, day: parts.day ?? 0)
    }
    init?(_ components: DateComponents) {
        guard let year = components.year, let month = components.month, let day = components.day else { return nil }
        self.init(year: year, month: month, day: day)
    }
    var components: DateComponents { DateComponents(year: year, month: month, day: day) }
    /// Seoul midnight of the day.
    var date: Date { Self.calendar.date(from: components) ?? .distantPast }
    var month1: SeoulDay { SeoulDay(year: year, month: month, day: 1) }
    static func < (lhs: SeoulDay, rhs: SeoulDay) -> Bool { (lhs.year, lhs.month, lhs.day) < (rhs.year, rhs.month, rhs.day) }
}

/// 즐겨찾기 활동 캘린더 보기 (contract 2026-10-25): each favorite activity's dates. The recruitment window's start (`모집 시작`) and last day
/// (`마감`) — a timed end counts on the Seoul day of the instant before it, a date-only end on the day before its exclusive end — and, for an
/// activity without any recruitment window, the activity start (`행사`). Activities with none of these are `날짜 미정`.
struct FavoriteDate: Identifiable, Sendable {
    enum Kind: Sendable, Hashable {
        case start, deadline, event
        var label: String {
            switch self {
            case .start: "모집 시작"
            case .deadline: "마감"
            case .event: "행사"
            }
        }
    }
    let item: FavoriteActivity
    let kind: Kind
    let day: SeoulDay
    var id: String { "\(item.id)|\(kind)" }

    /// Dated entries in day order (deadline before start on one day), and the undated activities in list order.
    static func build(_ items: [FavoriteActivity]) -> (dated: [FavoriteDate], undated: [FavoriteActivity]) {
        var dated: [FavoriteDate] = [], undated: [FavoriteActivity] = []
        for item in items {
            let found = dates(item.activity).map { FavoriteDate(item: item, kind: $0.kind, day: SeoulDay($0.date)) }
            if found.isEmpty { undated.append(item) } else { dated += found }
        }
        let order: [Kind: Int] = [.deadline: 0, .start: 1, .event: 2]
        return (dated.sorted { ($0.day, order[$0.kind] ?? 0, $0.item.activity.title) < ($1.day, order[$1.kind] ?? 0, $1.item.activity.title) }, undated)
    }
    private static func dates(_ activity: ActivityModel) -> [(kind: Kind, date: Date)] {
        let hasWindow = activity.recruitmentStartAt != nil || activity.recruitmentEndAt != nil
            || activity.recruitmentStartDate != nil || activity.recruitmentEndDateExclusive != nil
        guard hasWindow else { return (activity.activityStage?.anchor).map { [(.event, $0)] } ?? [] }
        let start = ActivityModel.instant(activity.recruitmentStartAt) ?? ActivityModel.seoulDay(activity.recruitmentStartDate)
        let end = ActivityModel.instant(activity.recruitmentEndAt).map { $0.addingTimeInterval(-1) }
            ?? ActivityModel.seoulDay(activity.recruitmentEndDateExclusive).flatMap { SeoulDay.calendar.date(byAdding: .day, value: -1, to: $0) }
        return [start.map { (Kind.start, $0) }, end.map { (Kind.deadline, $0) }].compactMap { $0 }
    }
}
