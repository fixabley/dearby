import EventKit
import Foundation

/// Reads busy times from the device calendar, on the device only (contract #148): event titles and times are
/// never sent or stored, and nothing is written. Access is asked for only from the overlap check.
struct DeviceCalendar: Sendable {
    enum Access: Equatable, Sendable { case allowed, denied }
    let requestAccess: @Sendable () async -> Access
    let busy: @Sendable (DateInterval) async -> [DateInterval]

    // EKEventStore is not Sendable, so each call makes its own; access is granted per app, not per store.
    static let live = DeviceCalendar(
        requestAccess: {
                switch EKEventStore.authorizationStatus(for: .event) {
                case .fullAccess: return .allowed
                case .notDetermined: return (try? await EKEventStore().requestFullAccessToEvents()) == true ? .allowed : .denied
                default: return .denied
                }
            },
        busy: { range in
                let store = EKEventStore()
                let predicate = store.predicateForEvents(withStart: range.start, end: range.end, calendars: nil)
                return store.events(matching: predicate)
                    .filter { $0.availability != .free && $0.startDate < $0.endDate }
                    .map { DateInterval(start: $0.startDate, end: $0.endDate) }
        })
}
