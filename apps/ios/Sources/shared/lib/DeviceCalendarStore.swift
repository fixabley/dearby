import EventKit
import Foundation

struct DeviceCalendarOption: Identifiable, Sendable {
    let id: String
    let title: String
}
struct DeviceBusyTime: Sendable, Equatable {
    let start: Date
    let end: Date
}
enum DeviceCalendarError: Error { case permission, unavailable }

// EventKit objects never leave this actor. Only calendar choices and anonymous times do.
actor DeviceCalendarStore {
    private let store = EKEventStore()
    func calendars(request: Bool) async throws -> [DeviceCalendarOption] {
        if EKEventStore.authorizationStatus(for: .event) == .notDetermined && request {
            _ = try await store.requestFullAccessToEvents()
        }
        guard EKEventStore.authorizationStatus(for: .event) == .fullAccess else {
            throw DeviceCalendarError.permission
        }
        defer { store.reset() }
        return store.calendars(for: .event).map { DeviceCalendarOption(id: $0.calendarIdentifier, title: $0.title) }
            .sorted { $0.title.localizedStandardCompare($1.title) == .orderedAscending }
    }
    static func blocksTime(status: EKEventStatus, availability: EKEventAvailability) -> Bool {
        status != .canceled && availability != .free
    }
    func busy(start: Date, end: Date, calendarIDs: Set<String>) throws -> [DeviceBusyTime] {
        var cursor = start
        var result: [DeviceBusyTime] = []
        while cursor < end {
            try Task.checkCancellation()
            let upper = min(cursor.addingTimeInterval(366 * 86_400), end)
            result += try readWindow(start: cursor, end: upper, calendarIDs: calendarIDs)
            cursor = upper
        }
        return result
    }
    private func readWindow(start: Date, end: Date, calendarIDs: Set<String>) throws -> [DeviceBusyTime] {
        guard EKEventStore.authorizationStatus(for: .event) == .fullAccess else { throw DeviceCalendarError.permission }
        defer { store.reset() }
        let calendars = store.calendars(for: .event).filter { calendarIDs.contains($0.calendarIdentifier) }
        guard calendars.count == calendarIDs.count, !calendars.isEmpty else { throw DeviceCalendarError.unavailable }
        let predicate = store.predicateForEvents(withStart: start, end: end, calendars: calendars)
        let times = store.events(matching: predicate).compactMap { event -> DeviceBusyTime? in
            guard Self.blocksTime(status: event.status, availability: event.availability),
                  let begin = event.startDate, let finish = event.endDate, begin < finish,
                  begin < end, start < finish else { return nil }
            return DeviceBusyTime(start: begin, end: finish)
        }
        guard EKEventStore.authorizationStatus(for: .event) == .fullAccess else { throw DeviceCalendarError.permission }
        return times
    }
}
