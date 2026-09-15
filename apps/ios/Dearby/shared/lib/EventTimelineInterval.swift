import Foundation

/// A display-only, half-open interval with an explicit source timezone.
struct EventTimelineInterval {
    let start: Date
    let end: Date
    let timeZone: TimeZone
    var calendar: Calendar {
        var value = Calendar(identifier: .gregorian)
        value.timeZone = timeZone
        return value
    }
    init?(start: Date, end: Date, timeZone: TimeZone) {
        guard start.timeIntervalSinceReferenceDate.isFinite, end.timeIntervalSinceReferenceDate.isFinite, start < end else { return nil }
        self.start = start; self.end = end; self.timeZone = timeZone
    }
    var days: ClosedRange<Date> {
        let first = calendar.startOfDay(for: start)
        let endDay = calendar.startOfDay(for: end)
        // Midnight is an exclusive boundary, not a new day containing an event.
        let last = end == endDay ? calendar.date(byAdding: .day, value: -1, to: endDay)! : endDay
        return first...last
    }
    func adjacentDay(to date: Date, by value: Int) -> Date? {
        guard let next = calendar.date(byAdding: .day, value: value, to: calendar.startOfDay(for: date)), days.contains(next) else { return nil }
        return next
    }
    struct Day {
        struct Tick: Identifiable {
            let date: Date
            let label: String
            let offset: Double
            var id: Date { date }
        }
        let start: Date
        let end: Date
        let clippedStart: Date
        let clippedEnd: Date
        let ticks: [Tick]
        let summary: String
        var hours: Double { end.timeIntervalSince(start) / 3600 }
        var offset: Double { clippedStart.timeIntervalSince(start) / 3600 }
        var duration: Double { clippedEnd.timeIntervalSince(clippedStart) / 3600 }
    }
    func day(containing selection: Date) -> Day? {
        let date = calendar.startOfDay(for: selection)
        guard days.contains(date), let bounds = calendar.dateInterval(of: .day, for: date) else { return nil }
        let lower = max(start, bounds.start), upper = min(end, bounds.end)
        guard lower < upper else { return nil }
        func formatter(_ format: String) -> DateFormatter {
            let value = DateFormatter()
            value.locale = Locale(identifier: "ko_KR"); value.calendar = calendar; value.timeZone = timeZone
            value.dateFormat = format
            return value
        }
        let shiftedDay = bounds.duration != 86400
        func time(_ date: Date) -> String {
            let seconds = calendar.component(.second, from: date), minutes = calendar.component(.minute, from: date)
            let format = seconds != 0 ? "a h시 m분 s초" : minutes != 0 ? "a h시 m분" : "a h시"
            return formatter(format + (shiftedDay ? " (ZZZZZ)" : "")).string(from: date)
        }
        var ticks: [Day.Tick] = []
        var tick = bounds.start
        // Only this day's hours are materialized, never all days of a long interval.
        while tick <= bounds.end && ticks.count < 28 {
            let label = tick == bounds.end ? "다음날 0시" : time(tick)
            ticks.append(.init(date: tick, label: label, offset: tick.timeIntervalSince(bounds.start) / 3600))
            guard let next = calendar.date(byAdding: .hour, value: 1, to: tick), next > tick else { break }
            tick = next
        }
        if ticks.last?.date != bounds.end {
            ticks.append(.init(date: bounds.end, label: "다음날 0시", offset: bounds.duration / 3600))
        }
        let endText = upper == bounds.end ? "다음날 오전 12시" : time(upper)
        return Day(start: bounds.start, end: bounds.end, clippedStart: lower, clippedEnd: upper, ticks: ticks,
                   summary: "\(time(lower))부터 \(endText)까지")
    }
}
