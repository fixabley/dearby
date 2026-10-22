import EventKit
import Foundation

/// One busy block read from a calendar, kept only while the overlap sheet is open (contract "기기 캘린더 겹침").
struct CalendarBusyEvent: Equatable, Sendable {
    /// The device event this occurrence belongs to (EventKit `calendarItemIdentifier`, shared by detached occurrences): results group by it.
    var eventID: String
    let title: String
    let interval: DateInterval
    /// Pattern text of a repeating device event (`매주 화`); nil for a single event. `반복 일정` when the rule cannot be read as text.
    var repeats: String? = nil
}

enum CalendarAccess: Equatable, Sendable {
    case granted
    /// The user declined, or a profile/parental setting blocks calendars.
    case denied
}

/// Where busy blocks come from. The app uses the device calendar; previews use a fixture so CI never touches EventKit.
@MainActor protocol CalendarEventSource {
    func requestAccess() async -> CalendarAccess
    /// Events intersecting `window`, leaving out those whose notes or URL contain `excluding` (the activity's own link, so an event added
    /// from the app does not overlap itself). Only called after `requestAccess()` returned `.granted`.
    func events(in window: DateInterval, excluding: String) -> [CalendarBusyEvent]
}

/// EventKit, read only. Access is requested when the user taps `겹치는 시간 확인`, never earlier (contract rule).
@MainActor final class DeviceCalendarEventSource: CalendarEventSource {
    private let store = EKEventStore()
    func requestAccess() async -> CalendarAccess {
        switch EKEventStore.authorizationStatus(for: .event) {
        case .fullAccess: return .granted
        case .denied, .restricted, .writeOnly: return .denied
        case .notDetermined, .authorized: break
        @unknown default: break
        }
        // iOS 18 deployment target: the iOS 17+ full-access request is always available.
        let granted = (try? await store.requestFullAccessToEvents()) ?? false
        return granted ? .granted : .denied
    }
    func events(in window: DateInterval, excluding: String) -> [CalendarBusyEvent] {
        let predicate = store.predicateForEvents(withStart: window.start, end: window.end, calendars: nil)
        // 종일 일정은 겹침에 넣지 않는다(계약, 2026-10-19 플랫폼 공통 결정): 시각이 있는 일정만 비교한다
        return store.events(matching: predicate).compactMap { event in
            guard !event.isAllDay, let start = event.startDate, let end = event.endDate, start < end,
                  !(event.notes ?? "").contains(excluding), !(event.url?.absoluteString ?? "").contains(excluding) else { return nil }
            let title = event.title?.trimmingCharacters(in: .whitespacesAndNewlines) ?? ""
            let repeats = event.hasRecurrenceRules || event.isDetached ? Self.pattern(event) : nil
            return CalendarBusyEvent(eventID: event.calendarItemIdentifier, title: title.isEmpty ? "제목 없는 일정" : title,
                                     interval: DateInterval(start: start, end: end), repeats: repeats)
        }
    }
    /// `매주 화 10:00~11:00` from the event's first rule (daily or weekly); `반복 일정` for anything else (monthly, yearly, none readable).
    private static func pattern(_ event: EKEvent) -> String {
        guard let rule = event.recurrenceRules?.first, rule.frequency == .daily || rule.frequency == .weekly else { return "반복 일정" }
        let days = rule.daysOfTheWeek?.map(\.dayOfTheWeek.rawValue) ?? [Calendar.current.component(.weekday, from: event.startDate)]
        let text = ScheduleRecurrence.text(frequency: rule.frequency == .daily ? .daily : .weekly, interval: rule.interval, weekdays: days)
        func time(_ date: Date) -> String { ActivityText.format(date, "HH:mm", zone: .current) }
        return "\(text) \(time(event.startDate))~\(time(event.endDate))"
    }
}

/// Preview-only source: one fixed busy hour (2026-10-24 14:00–15:00 Asia/Seoul). Not used by the app.
@MainActor final class FixtureCalendarEventSource: CalendarEventSource {
    static let exampleBusy = DateInterval(start: Date(timeIntervalSince1970: 1_792_818_000), duration: 3_600)
    private let access: CalendarAccess
    init(access: CalendarAccess = .granted) { self.access = access }
    func requestAccess() async -> CalendarAccess { access }
    func events(in window: DateInterval, excluding: String) -> [CalendarBusyEvent] {
        window.intersects(Self.exampleBusy) ? [CalendarBusyEvent(eventID: "fixture", title: "예시 바쁜 시간", interval: Self.exampleBusy)] : []
    }
}
