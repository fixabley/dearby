import Foundation
import Observation

struct CalendarOverlapState: Identifiable {
    let id: String
    let activity: ActivityScheduleModel
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

/// Compares an activity's sessions with the device calendar's busy times, on the device only.
@MainActor @Observable final class CalendarConflictState {
    enum Phase: Equatable { case idle, checking, denied, compared }
    let schedules: [ActivityScheduleModel]
    private(set) var phase = Phase.idle
    private(set) var overlaps: [CalendarOverlapState] = []
    var index = 0
    private let calendar: DeviceCalendar
    init(schedules: [ActivityScheduleModel], calendar: DeviceCalendar = .live) {
        self.schedules = schedules
        self.calendar = calendar
    }
    var compared: Bool { phase == .compared }
    // False means the final result (including zero overlaps) should dismiss.
    func advance() -> Bool {
        guard index + 1 < overlaps.count else { return false }
        index += 1
        return true
    }
    /// Asks for calendar access (once, by the system) and compares every session with every busy time.
    func compare() async {
        let sessions = schedules.filter { $0.start < $0.end }
        guard let first = sessions.map(\.start).min(), let last = sessions.map(\.end).max() else { return }
        phase = .checking
        guard await calendar.requestAccess() == .allowed else { phase = .denied; return }
        let busy = await calendar.busy(DateInterval(start: first, end: last))
        overlaps = Self.overlaps(sessions, busy)
        index = 0
        phase = .compared
    }
    /// Every (session, busy time) pair that shares time, in session then start order. Touching ends do not overlap.
    static func overlaps(_ sessions: [ActivityScheduleModel], _ busy: [DateInterval]) -> [CalendarOverlapState] {
        sessions.flatMap { session in
            busy.filter { CalendarOverlapState.matches(session, $0) }.sorted { $0.start < $1.start }
                .enumerated().map { CalendarOverlapState(id: "\(session.id)-\($0.offset)", activity: session, busy: $0.element) }
        }
    }
}
