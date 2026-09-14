import Foundation

@main
struct CalendarDraftTests {
    static func expectDays(_ interval: CalendarEventInterval?, _ start: String, _ end: String) {
        guard case .allDay(let lower, let upper) = interval else { preconditionFailure("Expected all-day interval") }
        let calendar = CalendarDatePolicy.calendar("Asia/Seoul")!
        precondition(lower == calendar.dateComponents([.year, .month, .day], from: CalendarDatePolicy.day(start, calendar: calendar)!))
        precondition(upper == calendar.dateComponents([.year, .month, .day], from: CalendarDatePolicy.day(end, calendar: calendar)!))
    }

    static func main() throws {
        let catalog = try JSONDecoder().decode(ActivityCatalog.self, from: Data(contentsOf: URL(fileURLWithPath: CommandLine.arguments[1])))
        let krc = catalog.activities.first { $0.favoriteOrganizationId == "krc" }!
        let db = catalog.activities.first { $0.favoriteOrganizationId == "db-insurance" }!
        for notice in [krc, db] {
            let draft = CalendarDraftMapper.application(notice, catalog: catalog)!
            guard case .timed(let start, let end, let zone) = draft.interval else { preconditionFailure() }
            precondition(start == CalendarDatePolicy.instant(notice.application.opensAt!))
            precondition(end == CalendarDatePolicy.instant(notice.application.closesAt!))
            precondition(zone == "Asia/Seoul" && draft.title.hasPrefix("[신청 기간]"))
            let request = CalendarEditorRequest(draft: draft, deviceTimeZone: TimeZone(identifier: "America/Los_Angeles")!)!
            precondition(request.start == start && request.end == end && !request.isAllDay)
            precondition(request.timezone?.identifier == "Asia/Seoul")
        }
        let contest = catalog.activities.first { $0.favoriteOrganizationId == "yeongnam-cyber-defense" }!
        expectDays(CalendarDraftMapper.application(contest, catalog: catalog)?.interval, "2026-09-11", "2026-10-08")
        func interval(_ startAt: String? = nil, _ startOn: String? = nil, _ endAt: String? = nil, _ endOn: String? = nil, allowEndOnly: Bool = true) -> CalendarEventInterval? {
            CalendarDatePolicy.interval(startAt: startAt, startOn: startOn, endAt: endAt, endOn: endOn, timezone: nil, allowEndOnly: allowEndOnly)
        }
        expectDays(interval(nil, nil, "2026-10-08T00:00:00+09:00"), "2026-10-07", "2026-10-08")
        expectDays(interval(nil, nil, "2026-10-08T13:00:00+09:00"), "2026-10-08", "2026-10-09")
        expectDays(interval(nil, "2026-10-01", nil, "2026-10-02"), "2026-10-01", "2026-10-03")
        expectDays(interval("2026-10-01T15:00:00+09:00"), "2026-10-01", "2026-10-02")
        expectDays(interval(nil, "2026-10-01", "2026-10-02T09:00:00+09:00"), "2026-10-01", "2026-10-03")
        precondition(interval("2026-10-01T15:00:00+09:00", "2026-10-02", "2026-10-03T15:00:00+09:00") == nil)
        expectDays(interval(nil, "2026-10-01", "2026-10-02T00:00:00+09:00", "2026-10-02"), "2026-10-01", "2026-10-02")
        precondition(interval() == nil)
        precondition(interval(nil, nil, nil, "2026-10-02", allowEndOnly: false) == nil)
        for bad in ["2026-02-30", "2026-13-01", "2026-1-01", "now", ""] {
            precondition(interval(nil, bad, nil, "2026-10-02") == nil)
            precondition(interval(nil, "2026-10-01", nil, bad) == nil)
        }
        for bad in ["2026-02-30T12:00:00+09:00", "2026-10-01T24:00:00+09:00", "2026-10-01T12:60:00+09:00", "2026-10-01T12:00:00", "garbage"] {
            precondition(interval(bad, "2026-10-01", nil, "2026-10-02") == nil)
        }
        precondition(interval("2026-10-02T12:00:00+09:00", nil, "2026-10-01T12:00:00+09:00") == nil)
        precondition(interval(nil, "2026-10-02", nil, "2026-10-01") == nil)
        precondition(interval(nil, "2026-10-02", "2026-10-02T00:00:00+09:00") == nil)
        precondition(CalendarDatePolicy.interval(startAt: nil, startOn: "2026-10-01", endAt: nil, endOn: nil, timezone: "Bad/Zone", allowEndOnly: false) == nil)
        for raw in [nil, "", "javascript:alert(1)", "https://", "https://user:password@example.com", "https://example.com/has space"] as [String?] {
            precondition(CalendarDraftMapper.verifiedURL(raw) == nil)
        }
        precondition(CalendarDraftMapper.verifiedURL("https://example.com/신청?a=1&b=2#확인") != nil)
        var raw = try JSONSerialization.jsonObject(with: Data(contentsOf: URL(fileURLWithPath: CommandLine.arguments[1]))) as! [String: Any]
        var notices = raw["activities"] as! [[String: Any]]
        let index = notices.firstIndex { $0["id"] as? String == krc.id }!
        var application = notices[index]["application"] as! [String: Any]
        application["url"] = "https://example.com/apply?a=1&b=2#신청"
        application["opensAt"] = NSNull(); application["opensOn"] = NSNull()
        notices[index]["application"] = application; raw["activities"] = notices
        let changed = try JSONDecoder().decode(ActivityCatalog.self, from: JSONSerialization.data(withJSONObject: raw))
        let deadline = CalendarDraftMapper.application(changed.activities[index], catalog: changed)!
        precondition(deadline.title.hasPrefix("[신청 마감]"))
        precondition(deadline.url == CalendarDraftMapper.verifiedURL(application["url"] as? String))
        precondition(deadline.notes.contains("신청 URL:") && deadline.notes.contains("원문:"))
        application.removeValue(forKey: "url"); application["closesAt"] = NSNull(); application["closesOn"] = NSNull(); application["opensOn"] = "2026-10-01"
        notices[index]["application"] = application; raw["activities"] = notices
        let noURL = try JSONDecoder().decode(ActivityCatalog.self, from: JSONSerialization.data(withJSONObject: raw))
        let draft = CalendarDraftMapper.application(noURL.activities[index], catalog: noURL)!
        precondition(draft.url == nil && draft.notes.contains("신청 마감: 미확인") && draft.notes.contains("신청 URL: 미확인"))
        for zone in ["Asia/Seoul", "America/Los_Angeles", "Pacific/Auckland"] {
            let request = CalendarEditorRequest(draft: draft, deviceTimeZone: TimeZone(identifier: zone)!)!
            let calendar = CalendarDatePolicy.calendar(zone)!
            precondition(request.isAllDay && calendar.component(.day, from: request.start) == 1 && calendar.component(.day, from: request.end) == 2)
            precondition(calendar.component(.hour, from: request.start) == 0)
        }
        print("PASS: application exact KST, deadline-only, contest midnight, strict invalid/reversed dates, mixed precision, inclusive dates, unknown end, URL/source separation, all-day device-zone conversion")
    }
}
