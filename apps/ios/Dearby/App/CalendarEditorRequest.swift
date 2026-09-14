import Foundation

/// Converts floating all-day calendar dates in the device zone only at the OS boundary.
struct CalendarEditorRequest: Identifiable {
    let id = UUID()
    let draft: CalendarEventDraft
    let start: Date
    let end: Date
    let isAllDay: Bool
    let timezone: TimeZone?

    init?(draft: CalendarEventDraft, deviceTimeZone: TimeZone = .current) {
        self.draft = draft
        switch draft.interval {
        case .timed(let start, let end, let zone):
            guard end > start, let timezone = TimeZone(identifier: zone) else { return nil }
            self.start = start; self.end = end; self.timezone = timezone; isAllDay = false
        case .allDay(let start, let end):
            var calendar = Calendar(identifier: .gregorian)
            calendar.timeZone = deviceTimeZone
            guard let lower = calendar.date(from: start), let upper = calendar.date(from: end), upper > lower,
                  calendar.dateComponents([.year, .month, .day], from: lower) == start,
                  calendar.dateComponents([.year, .month, .day], from: upper) == end else { return nil }
            self.start = lower; self.end = upper; timezone = nil; isAllDay = true
        }
    }

    static func prepare(_ draft: CalendarEventDraft, deviceTimeZone: TimeZone = .current,
                        present: (CalendarEditorRequest) -> Void, onFailure: () -> Void) {
        guard let request = CalendarEditorRequest(draft: draft, deviceTimeZone: deviceTimeZone) else {
            onFailure()
            return
        }
        present(request)
    }

}
