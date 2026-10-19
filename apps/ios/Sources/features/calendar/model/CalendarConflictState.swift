import Foundation
import Observation

struct CalendarOverlapState: Identifiable {
    let id: String
    let activity: ActivityScheduleModel
    /// The device event's title, shown only while the sheet is open.
    let busyTitle: String
    let busy: DateInterval
    var start: Date { max(activity.start, busy.start) }
    var end: Date { min(activity.end, busy.end) }
    var minutes: Int { Int(end.timeIntervalSince(start) / 60) }
    var gridStart: Date {
        let first = max(activity.start, start.addingTimeInterval(-3600))
        return Date(timeIntervalSince1970: floor(first.timeIntervalSince1970 / 1800) * 1800)
    }
    var gridEnd: Date { Date(timeIntervalSince1970: ceil(end.timeIntervalSince1970 / 1800) * 1800 + 1800) }
    var gridSteps: Int { Int(gridEnd.timeIntervalSince(gridStart) / 1800) }
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
    static func matches(_ activity: ActivityScheduleModel, _ busy: DateInterval) -> Bool {
        activity.start < activity.end && busy.start < busy.end && activity.start < busy.end && busy.start < activity.end
    }
}

/// Reads the device calendar once the user asks, compares on device, keeps nothing after the sheet closes
/// (contract "기기 캘린더 겹침 — 2026-10-06").
@MainActor @Observable final class CalendarConflictState {
    enum Phase: Equatable { case intro, requesting, denied, compared }
    let schedules: [ActivityScheduleModel]
    private let source: any CalendarEventSource
    private(set) var phase = Phase.intro
    private(set) var overlaps: [CalendarOverlapState] = []
    var index = 0
    init(schedules: [ActivityScheduleModel], source: any CalendarEventSource) {
        self.schedules = schedules
        self.source = source
    }
    // False means the final result (including zero overlaps) should dismiss.
    func advance() -> Bool {
        guard index + 1 < overlaps.count else { return false }
        index += 1
        return true
    }
    /// Asks for calendar access (first tap only shows the system prompt), then reads the window around the activity's schedules.
    func compare() async {
        guard phase != .requesting, let window = Self.window(schedules) else { return }
        phase = .requesting
        guard await source.requestAccess() == .granted else { phase = .denied; return }
        let events = source.events(in: window)
        overlaps = schedules.flatMap { schedule in
            events.compactMap { event in
                guard CalendarOverlapState.matches(schedule, event.interval) else { return nil }
                return CalendarOverlapState(id: "\(schedule.id)|\(event.interval.start.timeIntervalSince1970)|\(event.title)", activity: schedule, busyTitle: event.title, busy: event.interval)
            }
        }.sorted { $0.start < $1.start }
        index = 0
        phase = .compared
    }
    /// Earliest start to latest end of the activity's schedules; events intersecting it are fetched.
    static func window(_ schedules: [ActivityScheduleModel]) -> DateInterval? {
        guard let start = schedules.map(\.start).min(), let end = schedules.map(\.end).max(), start < end else { return nil }
        return DateInterval(start: start, end: end)
    }
}
