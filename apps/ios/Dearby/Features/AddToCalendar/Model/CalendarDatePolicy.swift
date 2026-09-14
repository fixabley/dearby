import Foundation

/// Strict shared temporal policy: no rollover, guessed duration or device-zone date extraction.
enum CalendarDatePolicy {
    static func calendar(_ timezone: String) -> Calendar? {
        guard let zone = TimeZone(identifier: timezone) else { return nil }
        var calendar = Calendar(identifier: .gregorian)
        calendar.timeZone = zone
        return calendar
    }

    static func day(_ raw: String, calendar: Calendar) -> Date? {
        guard raw.range(of: #"^\d{4}-\d{2}-\d{2}$"#, options: .regularExpression) != nil else { return nil }
        let formatter = DateFormatter()
        formatter.calendar = calendar
        formatter.locale = Locale(identifier: "en_US_POSIX")
        formatter.timeZone = calendar.timeZone
        formatter.dateFormat = "yyyy-MM-dd"
        formatter.isLenient = false
        guard let date = formatter.date(from: raw), formatter.string(from: date) == raw else { return nil }
        return date
    }

    static func instant(_ raw: String) -> Date? {
        guard raw.range(of: #"^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2}(?:\.\d+)?(?:Z|[+-]\d{2}:\d{2})$"#, options: .regularExpression) != nil,
              let utc = calendar("UTC"), day(String(raw.prefix(10)), calendar: utc) != nil else { return nil }
        let time = raw.dropFirst(11).prefix(8).split(separator: ":").compactMap { Int($0) }
        guard time.count == 3, time[0] < 24, time[1] < 60, time[2] < 60 else { return nil }
        let formatter = ISO8601DateFormatter()
        formatter.formatOptions = raw.contains(".") ? [.withInternetDateTime, .withFractionalSeconds] : [.withInternetDateTime]
        return formatter.date(from: raw)
    }

    static func interval(startAt: String?, startOn: String?, endAt: String?, endOn: String?,
                         timezone: String?, allowEndOnly: Bool) -> CalendarEventInterval? {
        let zone = timezone ?? "Asia/Seoul"
        guard let calendar = calendar(zone) else { return nil }
        let startTime = startAt.flatMap(instant)
        let endTime = endAt.flatMap(instant)
        let startDay = startOn.flatMap { day($0, calendar: calendar) }
        let endDay = endOn.flatMap { day($0, calendar: calendar) }
        guard startAt == nil || startTime != nil, endAt == nil || endTime != nil,
              startOn == nil || startDay != nil, endOn == nil || endDay != nil else { return nil }
        if let startTime, let startDay, calendar.startOfDay(for: startTime) != startDay { return nil }
        if let startTime, let endTime {
            guard endTime > startTime else { return nil }
            return .timed(start: startTime, end: endTime, timezone: zone)
        }
        let start = startDay ?? startTime.map { calendar.startOfDay(for: $0) }
        // Date-only end is inclusive; exact midnight timestamp is already exclusive.
        let end: Date?
        if let endTime {
            let midnight = calendar.startOfDay(for: endTime)
            end = endTime == midnight ? midnight : calendar.date(byAdding: .day, value: 1, to: midnight)
        } else if let endDay {
            end = calendar.date(byAdding: .day, value: 1, to: endDay)
        } else { end = nil }
        let lower: Date
        let upper: Date
        if let start {
            lower = start
            guard let resolvedEnd = end ?? calendar.date(byAdding: .day, value: 1, to: start), resolvedEnd > start else { return nil }
            upper = resolvedEnd
        } else if allowEndOnly, let end, let previous = calendar.date(byAdding: .day, value: -1, to: end) {
            lower = previous
            upper = end
        } else { return nil }
        return .allDay(start: calendar.dateComponents([.year, .month, .day], from: lower),
                       endExclusive: calendar.dateComponents([.year, .month, .day], from: upper))
    }
}
