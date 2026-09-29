import Foundation
import Observation

struct CalendarWindowState: Identifiable, Equatable {
    let id: String
    let title: String
    let start: Date
    let end: Date
    let timeZone: String
    static func parse(_ schedule: ActivityScheduleModel) -> CalendarWindowState? {
        guard let start = CatalogModel.date(schedule.startAt), let end = CatalogModel.date(schedule.endAt),
              start < end,
              TimeZone(identifier: schedule.timeZone) != nil else { return nil }
        return .init(id: schedule.id, title: schedule.title, start: start, end: end, timeZone: schedule.timeZone)
    }
}
struct CalendarOverlapState: Identifiable {
    let id: Int
    let activity: CalendarWindowState
    let busy: DeviceBusyTime
    var start: Date { max(activity.start, busy.start) }
    var end: Date { min(activity.end, busy.end) }
    static func matches(_ window: CalendarWindowState, _ busy: DeviceBusyTime) -> Bool {
        busy.start < busy.end && window.start < busy.end && busy.start < window.end
    }
}

@MainActor @Observable final class CalendarConflictState {
    enum Phase { case idle, loading, permission, noCalendars, choose, result, unknown, failed }
    let windows: [CalendarWindowState]
    let unknownCount: Int
    private let store: DeviceCalendarStore
    private var generation = 0
    private(set) var phase = Phase.idle
    private(set) var calendars: [DeviceCalendarOption] = []
    private(set) var selected: Set<String> = []
    private(set) var overlaps: [CalendarOverlapState] = []
    var index = 0
    init(schedules: [ActivityScheduleModel], store: DeviceCalendarStore = DeviceCalendarStore()) {
        windows = schedules.compactMap(CalendarWindowState.parse)
        unknownCount = schedules.isEmpty ? 1 : schedules.count - windows.count
        self.store = store
    }
    func connect(request: Bool) async {
        clear()
        guard !windows.isEmpty else { phase = .unknown; return }
        let ticket = generation
        phase = .loading
        do {
            let choices = try await store.calendars(request: request)
            guard ticket == generation, !Task.isCancelled else { return }
            calendars = choices
            selected = Set(choices.map(\.id))
            phase = choices.isEmpty ? .noCalendars : .choose
        } catch {
            guard ticket == generation, !Task.isCancelled else { return }
            phase = (error as? DeviceCalendarError) == .permission ? .permission : .failed
        }
    }
    func toggle(_ id: String) {
        if !selected.insert(id).inserted { selected.remove(id) }
        overlaps = []; index = 0; phase = .choose
    }
    func compare() async {
        guard !selected.isEmpty else { phase = .choose; return }
        generation += 1
        let ticket = generation
        overlaps = []; index = 0; phase = .loading
        do {
            var found: [CalendarOverlapState] = []
            for window in windows {
                try Task.checkCancellation()
                let busy = try await store.busy(start: window.start, end: window.end, calendarIDs: selected)
                guard ticket == generation, !Task.isCancelled else { return }
                for time in Set(busy.map { DateInterval(start: $0.start, end: $0.end) }).sorted(by: { $0.start < $1.start }) {
                    let value = DeviceBusyTime(start: time.start, end: time.end)
                    if CalendarOverlapState.matches(window, value) {
                        found.append(.init(id: found.count, activity: window, busy: value))
                    }
                }
            }
            guard ticket == generation, !Task.isCancelled else { return }
            overlaps = found.sorted { $0.start < $1.start }
            phase = .result
        } catch {
            guard ticket == generation, !Task.isCancelled else { return }
            overlaps = []
            phase = (error as? DeviceCalendarError) == .permission ? .permission : .failed
        }
    }
    func clear() {
        generation += 1
        overlaps = []; calendars = []; selected = []; index = 0; phase = .idle
    }
}
