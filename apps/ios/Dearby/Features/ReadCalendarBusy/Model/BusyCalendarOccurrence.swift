import Foundation

/// Provider-local filtering facts. No identifier/title/location/attendee payload is retained.
struct BusyCalendarOccurrence {
    let start: Date
    let end: Date
    let cancelled: Bool
    let explicitlyFree: Bool
    let declinedByMe: Bool
    var interval: BusyTimeInterval? {
        guard !cancelled, !explicitlyFree, !declinedByMe else { return nil }
        return BusyTimeInterval(start: start, end: end)
    }
}
