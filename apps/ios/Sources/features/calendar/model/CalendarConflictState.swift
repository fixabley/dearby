import Foundation
import Observation

struct CalendarOverlapState: Identifiable {
    let id: String
    let activity: ActivityScheduleModel
    let busy: DateInterval
    var start: Date { max(activity.start, busy.start) }
    var end: Date { min(activity.end, busy.end) }
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
    func compare() {
        guard selected else { return }
        overlaps = schedules.filter { CalendarOverlapState.matches($0, Self.exampleBusy) }
            .map { CalendarOverlapState(id: $0.id, activity: $0, busy: Self.exampleBusy) }
        index = 0
        compared = true
    }
}
