import EventKit
import Foundation

/// One busy block read from a calendar, kept only while the overlap sheet is open (contract "기기 캘린더 겹침").
struct CalendarBusyEvent: Equatable, Sendable {
    let title: String
    let interval: DateInterval
    /// All-day events carry their calendar days; they count only when they cover the activity's day.
    let allDay: Bool
}

enum CalendarAccess: Equatable, Sendable {
    case granted
    /// The user declined, or a profile/parental setting blocks calendars.
    case denied
}

/// Where busy blocks come from. The app uses the device calendar; previews use a fixture so CI never touches EventKit.
@MainActor protocol CalendarEventSource {
    func requestAccess() async -> CalendarAccess
    /// Events intersecting `window`. Only called after `requestAccess()` returned `.granted`.
    func events(in window: DateInterval) -> [CalendarBusyEvent]
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
    func events(in window: DateInterval) -> [CalendarBusyEvent] {
        let predicate = store.predicateForEvents(withStart: window.start, end: window.end, calendars: nil)
        return store.events(matching: predicate).compactMap { event in
            guard let start = event.startDate, let end = event.endDate, start < end else { return nil }
            let title = event.title?.trimmingCharacters(in: .whitespacesAndNewlines) ?? ""
            return CalendarBusyEvent(title: title.isEmpty ? "제목 없는 일정" : title, interval: DateInterval(start: start, end: end), allDay: event.isAllDay)
        }
    }
}

/// Preview-only source: one fixed busy hour (2026-10-24 14:00–15:00 Asia/Seoul). Not used by the app.
@MainActor final class FixtureCalendarEventSource: CalendarEventSource {
    static let exampleBusy = DateInterval(start: Date(timeIntervalSince1970: 1_792_818_000), duration: 3_600)
    private let access: CalendarAccess
    init(access: CalendarAccess = .granted) { self.access = access }
    func requestAccess() async -> CalendarAccess { access }
    func events(in window: DateInterval) -> [CalendarBusyEvent] {
        window.intersects(Self.exampleBusy) ? [CalendarBusyEvent(title: "예시 바쁜 시간", interval: Self.exampleBusy, allDay: false)] : []
    }
}
