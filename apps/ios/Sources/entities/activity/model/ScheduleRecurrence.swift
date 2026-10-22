import Foundation

/// A schedule's `recurrence` (contract "반복 일정과 겹침 결과 묶음" 2026-10-22): the RRULE subset the server accepts — `FREQ=DAILY|WEEKLY`,
/// `BYDAY`, `INTERVAL` 1–4, and `UNTIL` or `COUNT`. Anything else reads as no recurrence. Occurrences repeat on Seoul wall-clock days.
struct ScheduleRecurrence: Equatable, Sendable {
    enum Frequency: String, Sendable { case daily = "DAILY", weekly = "WEEKLY" }
    let frequency: Frequency
    let interval: Int
    /// `Calendar` weekdays (1 = Sunday … 7 = Saturday); empty means the first occurrence's weekday.
    let weekdays: [Int]
    /// Last instant an occurrence may start at (inclusive); a date-only `UNTIL` covers that whole Seoul day.
    let until: Date?
    let count: Int?
    static let limit = 400
    static let codes = ["SU", "MO", "TU", "WE", "TH", "FR", "SA"]

    init?(_ rule: String?) {
        guard let rule, !rule.isEmpty else { return nil }
        var parts: [String: String] = [:]
        for pair in rule.split(separator: ";") {
            let kv = pair.split(separator: "=", maxSplits: 1).map(String.init)
            guard kv.count == 2 else { return nil }
            parts[kv[0].uppercased()] = kv[1].uppercased()
        }
        guard let frequency = parts["FREQ"].flatMap(Frequency.init(rawValue:)) else { return nil }
        let interval = parts["INTERVAL"].map { Int($0) ?? 0 } ?? 1
        let weekdays = parts["BYDAY"].map { $0.split(separator: ",").map { Self.codes.firstIndex(of: String($0)).map { $0 + 1 } ?? 0 } } ?? []
        let count = parts["COUNT"].flatMap { Int($0) }
        let until = parts["UNTIL"].flatMap(Self.untilDate)
        guard (1...4).contains(interval), !weekdays.contains(0), (count == nil) != (until == nil),
              count.map({ (1...Self.limit).contains($0) }) ?? true else { return nil }
        self.frequency = frequency
        self.interval = interval
        self.weekdays = weekdays.sorted()
        self.until = until
        self.count = count
    }

    /// `yyyyMMdd` (through the end of that Seoul day) or `yyyyMMddTHHmmssZ`.
    private static func untilDate(_ raw: String) -> Date? {
        let formatter = DateFormatter()
        formatter.calendar = Calendar(identifier: .gregorian)
        formatter.locale = Locale(identifier: "en_US_POSIX")
        if raw.count == 8 {
            formatter.timeZone = ActivityText.seoul
            formatter.dateFormat = "yyyyMMdd"
            return formatter.date(from: raw)?.addingTimeInterval(86_400 - 1)
        }
        formatter.timeZone = TimeZone(identifier: "UTC")
        formatter.dateFormat = "yyyyMMdd'T'HHmmss'Z'"
        return formatter.date(from: raw)
    }

    /// Day offsets (0 = the first occurrence's Seoul day) of every occurrence starting at `first`, at most `limit`. Weekly rules count weeks
    /// from Monday (RFC 5545 default `WKST=MO`); days before `first` or off the rule are skipped.
    func dayOffsets(from first: Date) -> [Int] {
        var seoul = Calendar(identifier: .gregorian)
        seoul.timeZone = ActivityText.seoul
        let days = weekdays.isEmpty ? [seoul.component(.weekday, from: first)] : weekdays
        // Monday-based weekday index 0…6 of the first day, so week numbers start at that Monday.
        let firstIndex = (seoul.component(.weekday, from: first) + 5) % 7
        var offsets: [Int] = []
        var offset = 0
        while offsets.count < min(count ?? Self.limit, Self.limit), offset < Self.limit * 7 * interval {
            guard let start = seoul.date(byAdding: .day, value: offset, to: first) else { break }
            if let until, start > until { break }
            let matches: Bool = switch frequency {
            case .daily: offset % interval == 0
            case .weekly: days.contains(seoul.component(.weekday, from: start)) && ((firstIndex + offset) / 7) % interval == 0
            }
            if matches { offsets.append(offset) }
            offset += 1
        }
        return offsets
    }

    /// Korean pattern text, e.g. `평일`, `매주 화·목`, `2주마다 월`, `매일`, `2일마다`.
    var text: String { Self.text(frequency: frequency, interval: interval, weekdays: weekdays) }
    static func text(frequency: Frequency, interval: Int, weekdays: [Int]) -> String {
        let names = ["일", "월", "화", "수", "목", "금", "토"]
        switch frequency {
        case .daily: return interval == 1 ? "매일" : "\(interval)일마다"
        case .weekly:
            let sorted = weekdays.sorted { ($0 + 5) % 7 < ($1 + 5) % 7 }
            if interval == 1, sorted == [2, 3, 4, 5, 6] { return "평일" }
            if interval == 1, sorted == [7, 1] { return "주말" }
            let days = sorted.map { names[$0 - 1] }.joined(separator: "·")
            return (interval == 1 ? "매주" : "\(interval)주마다") + (days.isEmpty ? "" : " " + days)
        }
    }
}
