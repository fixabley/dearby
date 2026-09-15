import EventKit
import Foundation

/// Actor-confined EventKit adapter. Never exports EKEvent or private metadata; never saves.
actor EventKitBusyProvider: BusyCalendarProvider {
    private var eventStore: EKEventStore?
    func authorization() -> BusyCalendarAuthorization {
        switch EKEventStore.authorizationStatus(for: .event) {
        case .fullAccess: .fullAccess
        case .notDetermined, .writeOnly: .notRequested
        case .denied: .denied
        case .restricted: .restricted
        @unknown default: .restricted
        }
    }
    func requestReadPermission() async throws -> BusyCalendarAuthorization {
        if authorization() == .fullAccess { return .fullAccess }
        let store = eventStore ?? EKEventStore()
        eventStore = store
        let _: Bool = try await withCheckedThrowingContinuation { continuation in
            store.requestFullAccessToEvents { granted, error in
                if error != nil {
                    continuation.resume(throwing: BusyCalendarFailure.unavailable)
                } else {
                    continuation.resume(returning: granted)
                }
            }
        }
        return authorization()
    }
    func intervals(in range: DateInterval) throws -> [BusyTimeInterval] {
        try Task.checkCancellation()
        guard authorization() == .fullAccess else { throw BusyCalendarFailure.authorizationChanged }
        // A single selected source-zone day can be 23/24/25 hours; reject accidental broad reads.
        guard range.duration > 0, range.duration <= 27 * 3600 else { throw BusyCalendarFailure.unavailable }
        let store = eventStore ?? EKEventStore()
        eventStore = store
        defer { store.reset(); eventStore = nil }
        let predicate = store.predicateForEvents(withStart: range.start, end: range.end, calendars: nil)
        // EventKit expands recurring occurrences for this predicate; identifiers are intentionally unused.
        var values: [BusyTimeInterval] = []
        store.enumerateEvents(matching: predicate) { event, stop in
            if Task.isCancelled { stop.pointee = true; return }
            guard let start = event.startDate, let end = event.endDate else { return }
            if let interval = BusyCalendarOccurrence(start: start, end: end, cancelled: event.status == .canceled,
                explicitlyFree: event.availability == .free,
                declinedByMe: event.attendees?.contains { $0.isCurrentUser && $0.participantStatus == .declined } ?? false)
                .interval?.clipped(to: range) { values.append(interval) }
        }
        try Task.checkCancellation()
        guard authorization() == .fullAccess else { throw BusyCalendarFailure.authorizationChanged }
        return BusyTimeInterval.merged(values, in: range)
    }
    func discard() { eventStore?.reset(); eventStore = nil }
}
