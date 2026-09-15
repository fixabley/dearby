import Foundation

extension BusyTimeInterval {
    func description(in zone: TimeZone) -> String {
        var calendar = Calendar(identifier: .gregorian); calendar.timeZone = zone
        func time(_ value: Date, includeDate: Bool) -> String {
            let f = DateFormatter(); f.calendar = calendar; f.timeZone = zone; f.locale = Locale(identifier: "ko_KR")
            let seconds = calendar.component(.second, from: value), minutes = calendar.component(.minute, from: value)
            let clock = seconds != 0 ? "a h시 m분 s초" : minutes != 0 ? "a h시 m분" : "a h시"
            f.dateFormat = (includeDate ? "M월 d일 " : "") + clock + (zone.secondsFromGMT(for: start) != zone.secondsFromGMT(for: end) ? " (ZZZZZ)" : "")
            return f.string(from: value)
        }
        return "\(time(start, includeDate: false))부터 \(time(end, includeDate: !calendar.isDate(start, inSameDayAs: end)))까지"
    }
}
