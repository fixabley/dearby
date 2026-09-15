import Foundation

@main
struct EventTimelineTests {
    static func main() {
        func projection(_ a: String?, _ b: String?, startDay: String? = nil, endDay: String? = nil, zone: String = "Asia/Seoul") -> EventPeriodPresentation {
            EventPeriodPresentation(startsAt: a, startsOn: startDay, endsAt: b, endsOn: endDay, timezone: zone)
        }
        func date(_ raw: String) -> Date { ISO8601DateFormatter().date(from: raw)! }
        let many = projection("2026-08-27T09:00:00+09:00", "2026-09-15T13:00:00+09:00").timeline!
        let first = many.day(containing: many.start)!
        precondition(first.offset == 9 && first.duration == 15 && first.hours == 24)
        let middle = many.adjacentDay(to: many.start, by: 1)!
        let middleDay = many.day(containing: middle)!
        precondition(middleDay.offset == 0 && middleDay.duration == 24)
        let last = many.day(containing: many.days.upperBound)!
        precondition(last.offset == 0 && last.duration == 13)
        precondition(many.adjacentDay(to: many.start, by: -1) == nil && many.adjacentDay(to: many.days.upperBound, by: 1) == nil)
        precondition(many.day(containing: date("2026-09-16T00:00:00+09:00")) == nil)
        let midnight = projection("2026-12-31T23:00:00+09:00", "2027-01-01T00:00:00+09:00").timeline!
        precondition(midnight.days.lowerBound == midnight.days.upperBound)
        precondition(midnight.day(containing: midnight.start)!.duration == 1)
        let midnightMany = projection("2026-12-30T23:00:00+09:00", "2027-01-01T00:00:00+09:00").timeline!
        precondition(midnightMany.day(containing: midnightMany.days.upperBound)!.duration == 24)
        let short = projection("2026-09-15T09:00:00+09:00", "2026-09-15T09:00:12+09:00").timeline!.day(containing: date("2026-09-15T09:00:00+09:00"))!
        precondition(abs(short.duration * 3600 - 12) < 0.0001 && short.summary.contains("12초"))
        for value in [projection(nil, nil, startDay: "2026-09-15", endDay: "2026-09-16"),
                      projection(nil, "2026-09-15T13:00:00+09:00"),
                      projection("2026-09-15T09:00:00+09:00", nil, endDay: "2026-09-16"),
                      projection("bad", "2026-09-15T13:00:00+09:00"),
                      projection("2026-09-16T09:00:00+09:00", "2026-09-15T13:00:00+09:00"),
                      projection("2026-09-15T09:00:00+09:00", "2026-09-15T09:00:00+09:00"),
                      projection("2026-09-15T09:00:00+09:00", "2026-09-15T13:00:00+09:00", startDay: "2026-09-16")] { precondition(value.timeline == nil) }
        let spring = projection("2026-03-08T00:00:00-05:00", "2026-03-09T12:00:00-04:00", zone: "America/New_York").timeline!
        let springDay = spring.day(containing: spring.start)!
        precondition(springDay.hours == 23 && springDay.duration == 23 && springDay.ticks.count == 24)
        precondition(!springDay.ticks.contains { $0.label.hasPrefix("오전 2시") })
        precondition(spring.adjacentDay(to: spring.start, by: 1)!.timeIntervalSince(spring.days.lowerBound) == 23 * 3600)
        let fall = projection("2026-11-01T00:00:00-04:00", "2026-11-02T00:00:00-05:00", zone: "America/New_York").timeline!
        let fallDay = fall.day(containing: fall.start)!
        precondition(fallDay.hours == 25 && fallDay.ticks.count == 26)
        let ones = fallDay.ticks.filter { $0.label.hasPrefix("오전 1시") }
        precondition(ones.count == 2 && ones[0].label != ones[1].label && ones[0].id != ones[1].id)
        let fold = projection("2026-11-01T01:30:00-04:00", "2026-11-01T01:30:00-05:00", zone: "America/New_York")
        precondition(fold.lines[0].time.contains("-04:00") && fold.lines[0].time.contains("-05:00"))
        precondition(fold.timeline!.day(containing: fold.timeline!.start)!.duration == 1)
        let half = projection("2026-10-04T00:00:00+10:30", "2026-10-05T00:00:00+11:00", zone: "Australia/Lord_Howe").timeline!
        let halfDay = half.day(containing: half.start)!
        precondition(halfDay.hours == 23.5 && halfDay.ticks.last!.date == halfDay.end)
        let long = projection("2000-01-01T09:00:00+09:00", "2099-01-01T13:00:00+09:00").timeline!
        precondition(long.day(containing: long.days.upperBound)!.ticks.count == 25)
        print("PASS: timeline continuous clipping first/middle/last; date selection bounds; year/end-midnight exclusive; short exact duration; unknown/deadline/mixed/invalid/conflict/reversed/zero suppressed; DST23/25 and half-hour ticks; century interval bounded day projection")
    }
}
