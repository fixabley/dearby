import Foundation

/// Presentation only. Calendar drafts continue to consume the original structured fields.
enum CompactPeriod {
    static func period(start: String?, end: String?, timezone: String? = nil, fallback: String) -> String {
        guard start != nil || end != nil else { return fallback }
        let zone = timezone.flatMap(TimeZone.init(identifier:))
        func instant(_ value: String) -> Date? {
            let pieces = value.split(separator: "T", omittingEmptySubsequences: false)
            guard pieces.count == 2 else { return nil }
            let day = DateFormatter()
            day.locale = Locale(identifier: "en_US_POSIX")
            day.calendar = Calendar(identifier: .gregorian)
            day.timeZone = TimeZone(secondsFromGMT: 0)
            day.isLenient = false
            day.dateFormat = "yyyy-MM-dd"
            guard let parsed = day.date(from: String(pieces[0])), day.string(from: parsed) == pieces[0] else { return nil }
            return ISO8601DateFormatter().date(from: value)
        }
        func readable(_ value: String) -> String {
            let formatter = DateFormatter()
            formatter.locale = Locale(identifier: "en_US_POSIX")
            formatter.calendar = Calendar(identifier: .gregorian)
            formatter.timeZone = zone ?? TimeZone(secondsFromGMT: 0)!
            formatter.isLenient = false
            formatter.dateFormat = "yyyy-MM-dd"
            if let date = formatter.date(from: value), formatter.string(from: date) == value {
                formatter.dateFormat = "yyyy.M.d"
                return formatter.string(from: date) + " (시간 미확인)"
            }
            // Without a known source timezone retain the full timestamp, including its offset.
            if zone != nil, let date = instant(value) {
                formatter.dateFormat = formatter.calendar.component(.second, from: date) == 0 ? "yyyy.M.d HH:mm" : "yyyy.M.d HH:mm:ss"
                return formatter.string(from: date)
            }
            return value
        }
        if let timezone, zone == nil {
            return [start, end].compactMap { $0 }.joined(separator: " – ") + " (시간대 확인 필요: \(timezone))"
        }
        if let start, let end {
            let days = DateFormatter()
            days.locale = Locale(identifier: "en_US_POSIX")
            days.timeZone = TimeZone(secondsFromGMT: 0)
            days.dateFormat = "yyyy-MM-dd"
            days.isLenient = false
            let first = instant(start) ?? days.date(from: start)
            let last = instant(end) ?? days.date(from: end)
            if let first, let last, first > last {
                return "\(start) – \(end) (기간 순서 확인 필요)"
            }
        }
        let suffix = zone == nil ? "" : (timezone == "Asia/Seoul" ? " (한국 시간)" : " (\(timezone!))")
        if let start, let end, let zone,
           let first = instant(start),
           let last = instant(end) {
            var calendar = Calendar(identifier: .gregorian)
            calendar.timeZone = zone
            if calendar.isDate(first, inSameDayAs: last), first != last {
                let time = DateFormatter()
                time.locale = Locale(identifier: "en_US_POSIX")
                time.timeZone = zone
                time.dateFormat = calendar.component(.second, from: last) == 0 ? "HH:mm" : "HH:mm:ss"
                return "\(readable(start))–\(time.string(from: last))" + suffix
            }
        }
        if let start, let end {
            return (start == end ? readable(start) : "\(readable(start)) – \(readable(end))") + suffix
        }
        if let end { return "~ \(readable(end))" + suffix }
        return "\(readable(start!)) · 종료 미확인" + suffix
    }

}
