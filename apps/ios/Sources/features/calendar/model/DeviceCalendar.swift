import EventKit
import Foundation

/// Reads busy times from the device calendar, on the device only (contract #148): event titles and times are
/// never sent or stored, and nothing is written. Access is asked for only from the overlap check.
struct DeviceCalendar: Sendable {
    enum Access: Equatable, Sendable { case allowed, denied }
    let requestAccess: @Sendable () async -> Access
    let busy: @Sendable (DateInterval) async -> [DateInterval]

    static let live: DeviceCalendar = {
        #if DEBUG
        // UI tests replace the calendar: "denied", or busy times as "start/end;start/end" in ISO 8601.
        if let fixture = ProcessInfo.processInfo.environment["DEARBY_CALENDAR_BUSY"] { return .fixture(fixture) }
        #endif
        let store = EKEventStore()
        return DeviceCalendar(
            requestAccess: {
                switch EKEventStore.authorizationStatus(for: .event) {
                case .fullAccess: return .allowed
                case .notDetermined: return (try? await store.requestFullAccessToEvents()) == true ? .allowed : .denied
                default: return .denied
                }
            },
            busy: { range in
                let predicate = store.predicateForEvents(withStart: range.start, end: range.end, calendars: nil)
                return store.events(matching: predicate)
                    .filter { $0.availability != .free && $0.startDate < $0.endDate }
                    .map { DateInterval(start: $0.startDate, end: $0.endDate) }
            })
    }()

    static func fixture(_ text: String) -> DeviceCalendar {
        let parser = ISO8601DateFormatter()
        let intervals = text.split(separator: ";").compactMap { pair -> DateInterval? in
            let parts = pair.split(separator: "/").map(String.init)
            guard parts.count == 2, let start = parser.date(from: parts[0]), let end = parser.date(from: parts[1]), start < end else { return nil }
            return DateInterval(start: start, end: end)
        }
        return DeviceCalendar(requestAccess: { text == "denied" ? .denied : .allowed }, busy: { _ in intervals })
    }
}
