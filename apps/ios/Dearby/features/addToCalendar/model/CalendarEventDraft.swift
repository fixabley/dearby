import Foundation

/// Immutable export values; all-day dates remain source-local Gregorian dates.
struct CalendarEventDraft {
    let title: String
    let interval: CalendarEventInterval
    let location: String?
    let url: URL?
    let notes: String
}

enum CalendarEventInterval {
    case timed(start: Date, end: Date, timezone: String)
    case allDay(start: DateComponents, endExclusive: DateComponents)
}
