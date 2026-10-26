import Foundation
import Observation

struct CalendarOverlapState: Identifiable {
    let id: String
    let activity: ActivityScheduleModel
    /// The device event's title, shown only while the sheet is open.
    let busyTitle: String
    let busy: DateInterval
    let eventID: String
    let repeats: String?
    var start: Date { max(activity.start, busy.start) }
    var end: Date { min(activity.end, busy.end) }
    var minutes: Int { Int(end.timeIntervalSince(start) / 60) }
    /// The timeline's window and tick step (contract "반복 일정과 겹침 결과 묶음"): up to an hour of the activity before the overlap and 30
    /// minutes after it, snapped to the first step that keeps it to at most six intervals (about six labels), counted from local midnight
    /// in the activity's zone so a two-hour step lands on 08:00·10:00.
    var grid: (start: Date, end: Date, step: TimeInterval) {
        let from = max(activity.start, start.addingTimeInterval(-3600)), to = end.addingTimeInterval(1800)
        var calendar = Calendar(identifier: .gregorian)
        calendar.timeZone = TimeZone(identifier: activity.timeZone) ?? .current
        let midnight = calendar.startOfDay(for: from)
        let grids = [15, 30, 60, 120, 180, 240, 360, 720].map { minutes -> (start: Date, end: Date, step: TimeInterval) in
            let step = TimeInterval(minutes * 60)
            let start = midnight.addingTimeInterval(floor(from.timeIntervalSince(midnight) / step) * step)
            return (start, start.addingTimeInterval(ceil(to.timeIntervalSince(start) / step) * step), step)
        }
        return grids.first { $0.end.timeIntervalSince($0.start) / $0.step <= 6 } ?? grids[grids.count - 1]
    }
    var gridStart: Date { grid.start }
    var gridEnd: Date { grid.end }
    var dateFormat: Date.FormatStyle {
        Date.FormatStyle(locale: Locale(identifier: "ko_KR"), timeZone: TimeZone(identifier: activity.timeZone)!)
            .month(.wide).day().weekday(.abbreviated)
    }
    func time(_ date: Date) -> String {
        let formatter = DateFormatter()
        formatter.locale = Locale(identifier: "ko_KR")
        formatter.timeZone = TimeZone(identifier: activity.timeZone)
        formatter.dateFormat = "HH:mm"
        return formatter.string(from: date)
    }
    /// `23:00 – 01:00`, naming the end date when it falls on another day (`23:00 – 11월 2일 01:00`).
    func span(_ from: Date, _ to: Date) -> String {
        let day = { (date: Date) in ActivityText.format(date, "M월 d일", zone: activity.timeZone) }
        return "\(time(from)) – \(day(from) == day(to) ? "" : "\(day(to)) ")\(time(to))"
    }
    static func matches(_ activity: ActivityScheduleModel, _ busy: DateInterval) -> Bool {
        activity.start < activity.end && busy.start < busy.end && activity.start < busy.end && busy.start < activity.end
    }
}

/// One device event's overlaps with the activity (contract "겹침 결과 묶음" 2026-10-22): every occurrence of a repeating event, or the
/// occurrences of the activity a single event meets, in date order.
struct CalendarOverlapGroup: Identifiable {
    let id: String
    let title: String
    /// The device event's pattern (`매주 화 10:00~11:00`); nil for a single event.
    let repeats: String?
    let overlaps: [CalendarOverlapState]
    /// `n번 겹침(10월 28일~12월 16일)`, or the one date.
    var countText: String {
        guard let first = overlaps.first, let last = overlaps.last else { return "" }
        let day = { (date: Date) in ActivityText.format(date, "M월 d일", zone: first.activity.timeZone) }
        return overlaps.count == 1 ? "\(day(first.start)) 겹침" : "\(overlaps.count)번 겹침(\(day(first.start))~\(day(last.start)))"
    }
}

/// Reads the device calendar once the user asks, compares on device, keeps nothing after the sheet closes
/// (contract "기기 캘린더 겹침 — 2026-10-06"). Results are grouped per device event (contract "겹침 결과 묶음" 2026-10-22).
@MainActor @Observable final class CalendarConflictState {
    enum Phase: Equatable { case intro, requesting, denied, compared }
    let schedules: [ActivityScheduleModel]
    private let source: any CalendarEventSource
    /// The activity's link; device events carrying it (added from the app) are left out.
    private let excluding: String
    private(set) var phase = Phase.intro
    private(set) var overlaps: [CalendarOverlapState] = []
    /// Repeating device events, earliest overlap first.
    private(set) var repeating: [CalendarOverlapGroup] = []
    /// Single device events, nearest first.
    private(set) var single: [CalendarOverlapGroup] = []
    /// `n일 겹침 · 반복 일정 a개 · 단일 일정 b개`.
    var summary: String {
        let days = Set(overlaps.map { ActivityText.format($0.start, "yyyyMMdd", zone: $0.activity.timeZone) }).count
        return "\(days)일 겹침 · 반복 일정 \(repeating.count)개 · 단일 일정 \(single.count)개"
    }
    init(schedules: [ActivityScheduleModel], excluding: String, source: any CalendarEventSource) {
        self.schedules = schedules
        self.excluding = excluding
        self.source = source
    }
    /// Asks for calendar access (first tap only shows the system prompt), then reads the window around the activity's schedules.
    func compare() async {
        guard phase != .requesting, let window = Self.window(schedules) else { return }
        phase = .requesting
        guard await source.requestAccess() == .granted else { phase = .denied; return }
        // The same event synced into two calendars (same title and times) counts once, always under the smaller identifier so a
        // repeating event's occurrences stay in one group.
        var seen = Set<String>()
        let events = source.events(in: window, excluding: excluding).sorted { $0.eventID < $1.eventID }.filter {
            seen.insert("\($0.title)|\($0.interval.start.timeIntervalSince1970)|\($0.interval.end.timeIntervalSince1970)").inserted
        }
        overlaps = schedules.flatMap { schedule in
            events.compactMap { event in
                guard CalendarOverlapState.matches(schedule, event.interval) else { return nil }
                return CalendarOverlapState(id: "\(schedule.id)|\(event.eventID)|\(event.interval.start.timeIntervalSince1970)",
                                            activity: schedule, busyTitle: event.title, busy: event.interval, eventID: event.eventID,
                                            repeats: event.repeats)
            }
        }.sorted { $0.start < $1.start }
        let groups = Dictionary(grouping: overlaps, by: \.eventID).values.compactMap { items -> CalendarOverlapGroup? in
            guard let first = items.first else { return nil }
            return CalendarOverlapGroup(id: first.eventID, title: first.busyTitle, repeats: items.lazy.compactMap(\.repeats).first, overlaps: items)
        }.sorted { ($0.overlaps.first?.start ?? .distantFuture) < ($1.overlaps.first?.start ?? .distantFuture) }
        repeating = groups.filter { $0.repeats != nil }
        single = groups.filter { $0.repeats == nil }
        phase = .compared
    }
    /// Earliest start to latest end of the activity's schedules; events intersecting it are fetched.
    static func window(_ schedules: [ActivityScheduleModel]) -> DateInterval? {
        guard let start = schedules.map(\.start).min(), let end = schedules.map(\.end).max(), start < end else { return nil }
        return DateInterval(start: start, end: end)
    }
}
