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

@MainActor @Observable final class CalendarConflictState {
    // 2026-10-24 14:00–15:00 Asia/Seoul, fixed independently of the device calendar.
    static let exampleBusy = DateInterval(start: Date(timeIntervalSince1970: 1_792_818_000), duration: 3_600)
    let schedules: [ActivityScheduleModel]
    var selected = true {
        didSet { compared = false; overlaps = []; index = 0 }
    }
    private(set) var compared = false
    private(set) var overlaps: [CalendarOverlapState] = []
    var index = 0
    init(schedules: [ActivityScheduleModel]) { self.schedules = schedules }
    // False means the final result (including zero overlaps) should dismiss.
    func advance() -> Bool {
        guard index + 1 < overlaps.count else { return false }
        index += 1
        return true
    }
    func compare() {
        guard selected else { return }
        overlaps = schedules.filter { CalendarOverlapState.matches($0, Self.exampleBusy) }
            .map { CalendarOverlapState(id: $0.id, activity: $0, busy: Self.exampleBusy) }
        index = 0
        compared = true
    }
}
