import Foundation

/// Display-only structured date/time lines; never supplies dates to an OS event.
struct EventPeriodPresentation {
    struct Line: Equatable {
        let label: String?
        let date: String
        let time: String
    }
    let lines: [Line]
    let note: String?

    init(startsAt: String?, startsOn: String?, endsAt: String?, endsOn: String?, timezone: String?) {
        let raw = [startsAt, startsOn, endsAt, endsOn].compactMap { $0 }.joined(separator: " · ")
        let zone = timezone.flatMap(TimeZone.init(identifier:))
        func fallback(_ reason: String) -> Self {
            Self(lines: [.init(label: nil, date: raw.isEmpty ? "일정 미확인" : raw, time: reason)], note: timezone)
        }
        if timezone != nil && zone == nil { self = fallback("시간대 확인 필요"); return }
        struct Point {
            let date: Date
            let day: String
            let clock: String?
        }
        var calendar = Calendar(identifier: .gregorian)
        calendar.timeZone = zone ?? TimeZone(secondsFromGMT: 0)!
        func formatter(_ format: String) -> DateFormatter {
            let result = DateFormatter()
            result.locale = Locale(identifier: "en_US_POSIX")
            result.calendar = calendar
            result.timeZone = calendar.timeZone
            result.isLenient = false
            result.dateFormat = format
            return result
        }
        func point(at: String?, on: String?) -> Point? {
            let day = formatter("yyyy-MM-dd")
            if let on, !(day.date(from: on).map { day.string(from: $0) == on } ?? false) { return nil }
            if let at {
                // Round-trip requires valid fields and a known, matching source timezone.
                // Unsupported precision/offset forms remain visible verbatim in the fallback.
                guard zone != nil else { return nil }
                let iso = formatter("yyyy-MM-dd'T'HH:mm:ssXXXXX")
                guard let date = iso.date(from: at), iso.string(from: date) == at,
                      on == nil || day.string(from: date) == on else { return nil }
                let clock = formatter(calendar.component(.second, from: date) == 0 ? "HH:mm" : "HH:mm:ss")
                return Point(date: date, day: day.string(from: date), clock: clock.string(from: date))
            }
            guard let on, let date = day.date(from: on) else { return nil }
            return Point(date: date, day: on, clock: nil)
        }
        let first = point(at: startsAt, on: startsOn)
        let last = point(at: endsAt, on: endsOn)
        if (startsAt != nil || startsOn != nil) && first == nil || (endsAt != nil || endsOn != nil) && last == nil {
            self = fallback("날짜·시간 또는 원문 필드 일치 확인 필요"); return
        }
        if let first, let last, first.date > last.date {
            self = fallback("기간 순서 확인 필요"); return
        }
        let date = formatter("yyyy년 M월 d일")
        let zoneText = timezone.map { $0 == "Asia/Seoul" ? "한국 시간" : $0 }
        func line(_ point: Point, _ label: String?) -> Line {
            .init(label: label, date: date.string(from: point.date), time: point.clock ?? "시간 미확인")
        }
        if let first, let last {
            if first.day == last.day {
                let time: String
                if let start = first.clock, let end = last.clock { time = start == end ? start : "\(start)–\(end)" }
                else if first.clock == nil && last.clock == nil { time = "시간 미확인" }
                else { time = "시작 \(first.clock ?? "시간 미확인") · 종료 \(last.clock ?? "시간 미확인")" }
                self = Self(lines: [.init(label: nil, date: date.string(from: first.date), time: time)], note: zoneText)
            } else { self = Self(lines: [line(first, "시작"), line(last, "종료")], note: zoneText) }
        } else if let first {
            self = Self(lines: [line(first, nil)], note: ["종료 미확인", zoneText].compactMap { $0 }.joined(separator: " · "))
        } else if let last {
            self = Self(lines: [line(last, "마감")], note: ["시작 미확인", zoneText].compactMap { $0 }.joined(separator: " · "))
        } else { self = fallback("시간 미확인") }
    }

    private init(lines: [Line], note: String?) { self.lines = lines; self.note = note }
}
