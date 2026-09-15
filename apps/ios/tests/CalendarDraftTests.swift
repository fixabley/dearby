// Force casts below assert the bundled JSON fixture schema, never external input.
import Foundation

@main
struct CalendarDraftTests {
    static func expectDays(_ interval: CalendarEventInterval?, _ start: String, _ end: String) {
        guard case .allDay(let lower, let upper) = interval else { preconditionFailure("Expected all-day interval") }
        let calendar = CalendarDatePolicy.calendar("Asia/Seoul")!
        precondition(lower == calendar.dateComponents([.year, .month, .day], from: CalendarDatePolicy.day(start, calendar: calendar)!))
        precondition(upper == calendar.dateComponents([.year, .month, .day], from: CalendarDatePolicy.day(end, calendar: calendar)!))
    }

    @MainActor
    static func main() throws {
        let catalog = try JSONDecoder().decode(BundleSnapshot.self, from: Data(contentsOf: URL(fileURLWithPath: CommandLine.arguments[1])))
        let krc = catalog.notices.first { $0.favoriteOrganizationId == "krc" }!
        let db = catalog.notices.first { $0.favoriteOrganizationId == "db-insurance" }!
        for notice in [krc, db] {
            let draft = CalendarDraftMapper.application(detail(notice, catalog: catalog))!
            guard case .timed(let start, let end, let zone) = draft.interval else { preconditionFailure() }
            precondition(start == CalendarDatePolicy.instant(notice.applicationInformation.opensAt!))
            precondition(end == CalendarDatePolicy.instant(notice.applicationInformation.closesAt!))
            precondition(zone == "Asia/Seoul" && draft.title.hasPrefix("[신청 기간]"))
            let request = CalendarEditorRequest(draft: draft, deviceTimeZone: TimeZone(identifier: "America/Los_Angeles")!)!
            precondition(request.start == start && request.end == end && !request.isAllDay)
            precondition(request.timezone?.identifier == "Asia/Seoul")
        }
        let contest = catalog.notices.first { $0.favoriteOrganizationId == "yeongnam-cyber-defense" }!
        expectDays(CalendarDraftMapper.application(detail(contest, catalog: catalog))?.interval, "2026-09-11", "2026-10-08")
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
        for bad in ["2026-02-30T12:00:00+09:00", "2026-10-01T24:00:00+09:00", "2026-10-01T12:60:00+09:00", "2026-10-01T12:00:00", "2026-10-01T12:00:00+99:00", "2026-10-01T12:00:00+09:99", "garbage"] {
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
        var raw = try JSONSerialization.jsonObject(with: Data(contentsOf: URL(fileURLWithPath: CommandLine.arguments[1]))) as! [String: Any] // swiftlint:disable:this force_cast
        var notices = raw["activities"] as! [[String: Any]] // swiftlint:disable:this force_cast
        let index = notices.firstIndex { $0["id"] as? String == krc.id }!
        var application = notices[index]["application"] as! [String: Any] // swiftlint:disable:this force_cast
        application["url"] = "https://example.com/apply?a=1&b=2#신청"
        application["opensAt"] = NSNull(); application["opensOn"] = NSNull()
        notices[index]["application"] = application; raw["activities"] = notices
        let changed = try JSONDecoder().decode(BundleSnapshot.self, from: JSONSerialization.data(withJSONObject: raw))
        let deadline = CalendarDraftMapper.application(detail(changed.notices[index], catalog: changed))!
        precondition(deadline.title.hasPrefix("[신청 마감]"))
        precondition(deadline.url == CalendarDraftMapper.verifiedURL(application["url"] as? String))
        precondition(deadline.notes == detail(changed.notices[index], catalog: changed).sourceURL!.absoluteString)
        application.removeValue(forKey: "url"); application["closesAt"] = NSNull(); application["closesOn"] = NSNull(); application["opensOn"] = "2026-10-01"
        notices[index]["application"] = application; raw["activities"] = notices
        let noURL = try JSONDecoder().decode(BundleSnapshot.self, from: JSONSerialization.data(withJSONObject: raw))
        let draft = CalendarDraftMapper.application(detail(noURL.notices[index], catalog: noURL))!
        precondition(draft.url == nil && draft.notes == detail(noURL.notices[index], catalog: noURL).sourceURL!.absoluteString)
        for zone in ["Asia/Seoul", "America/Los_Angeles", "Pacific/Auckland"] {
            let request = CalendarEditorRequest(draft: draft, deviceTimeZone: TimeZone(identifier: zone)!)!
            let calendar = CalendarDatePolicy.calendar(zone)!
            precondition(request.isAllDay && calendar.component(.day, from: request.start) == 1 && calendar.component(.day, from: request.end) == 2)
            precondition(calendar.component(.hour, from: request.start) == 0)
        }
        var requests: [CalendarEditorRequest] = []
        var failures = 0
        CalendarEditorRequest.prepare(draft, deviceTimeZone: TimeZone(identifier: "Asia/Seoul")!,
                                      present: { requests.append($0) }, onFailure: { failures += 1 })
        precondition(requests.count == 1 && failures == 0 && requests[0].draft.notes == draft.notes)
        let reversed = CalendarEventDraft(title: "invalid", interval: .timed(start: Date(timeIntervalSince1970: 2), end: Date(timeIntervalSince1970: 1), timezone: "Asia/Seoul"), location: nil, url: nil, notes: "")
        CalendarEditorRequest.prepare(reversed, present: { _ in preconditionFailure("Invalid request must not present") }, onFailure: { failures += 1 })
        let malformed = CalendarEventDraft(title: "invalid", interval: .allDay(start: DateComponents(year: 2026, month: 2, day: 30), endExclusive: DateComponents(year: 2026, month: 3, day: 4)), location: nil, url: nil, notes: "")
        CalendarEditorRequest.prepare(malformed, present: { _ in preconditionFailure("Malformed dates must not present") }, onFailure: { failures += 1 })
        precondition(failures == 2)
        // Both application and activity notes use only the primary source, never event URL fallback.
        var sourceNotice = detail(changed.notices[index], catalog: changed)
        let sourceID = sourceNotice.sourceIds.first!
        for rawSource in [nil, "", "javascript:bad", "https://", "https://user:pass@example.com", "https://example.com/original?a=1&b=2#section", "http://example.com/original"] as [String?] {
            sourceNotice.sources = rawSource.map { [NoticeSource(id: sourceID, url: $0)] } ?? []
            let expected = CalendarDraftMapper.verifiedURL(rawSource)?.absoluteString ?? ""
            let applicationDraft = CalendarDraftMapper.application(sourceNotice)!
            precondition(applicationDraft.notes == expected)
            precondition(applicationDraft.url == deadline.url, "Application event URL must remain separate")
            let phase = NoticePhase(period: NoticeSchedule(phase: "online", startsOn: "2026-10-01", startsAt: nil, endsAt: nil, endsOn: nil, timezone: nil, mode: "online", onlineUrl: "https://example.com/join"), locations: [], locationSummary: "온라인")
            let activityDraft = CalendarDraftMapper.schedule(phase, detail: sourceNotice)!
            precondition(activityDraft.notes == expected && activityDraft.location == "온라인")
            precondition(activityDraft.url?.absoluteString == "https://example.com/join")
        }
        sourceNotice.sources = [NoticeSource(id: "unrelated", url: "https://example.com/not-primary")]
        precondition(CalendarDraftMapper.application(sourceNotice)!.notes.isEmpty)
        try testActivities(catalog: catalog)
        print("PASS: application exact KST, deadline-only, contest midnight, strict invalid/reversed dates, mixed precision, inclusive dates, unknown end, URL/source separation, all-day device-zone conversion")
    }

    @MainActor
    static func testActivities(catalog: BundleSnapshot) throws {
        let contest = catalog.notices.first { $0.favoriteOrganizationId == "yeongnam-cyber-defense" }!
        func draft(_ phase: NoticeSchedule, notice: NoticeModel? = nil) -> CalendarEventDraft? {
            let projected = detail(notice ?? contest, catalog: catalog, schedule: [phase])
            return CalendarDraftMapper.schedule(projected.schedules[0], detail: projected)
        }
        let preliminary = draft(contest.schedule.first { $0.phase == "preliminary" }!)!
        expectDays(preliminary.interval, "2026-10-14", "2026-10-15")
        precondition(preliminary.location == "온라인" && preliminary.url == nil)
        precondition(preliminary.notes == detail(contest, catalog: catalog).sourceURL!.absoluteString)
        let final = draft(contest.schedule.first { $0.phase == "final" }!)!
        expectDays(final.interval, "2026-11-04", "2026-11-05")
        precondition(final.location == contest.location.venues.first { $0.phase == "final" }!.name)
        precondition(final.notes == preliminary.notes)
        for target in ["krc", "db-insurance"] {
            let notice = catalog.notices.first { $0.favoriteOrganizationId == target }!
            let phase = notice.schedule[0]
            let event = draft(phase, notice: notice)!
            guard case .timed(let start, let end, let timezone) = event.interval else { preconditionFailure() }
            precondition(timezone == "Asia/Seoul" && start == CalendarDatePolicy.instant(phase.startsAt!) && end == CalendarDatePolicy.instant(phase.endsAt!))
            precondition(event.notes == detail(notice, catalog: catalog).sourceURL!.absoluteString)
        }
        func phase(_ mode: String = "online", url: String? = nil, name: String = "preliminary", start: String? = "2026-10-14", end: String? = nil) -> NoticeSchedule {
            NoticeSchedule(phase: name, startsOn: start, startsAt: nil, endsAt: nil, endsOn: end, timezone: nil, mode: mode, onlineUrl: url)
        }
        let online = draft(phase(url: "https://example.com/온라인?a=1&b=2#회의"))!
        precondition(online.url != nil && online.location == "온라인" && online.notes == preliminary.notes)
        precondition(draft(phase(url: "javascript:bad"))!.url == nil)
        precondition(draft(phase("offline", name: "unmatched"))!.location == "장소 미확인")
        precondition(!draft(phase("offline", name: "unmatched"))!.notes.contains("콘퍼런스"))
        expectDays(draft(phase(end: "2026-10-16"))?.interval, "2026-10-14", "2026-10-17")
        precondition(draft(phase(start: nil)) == nil)
        precondition(draft(phase(start: "2026-02-30")) == nil)
        precondition(draft(phase(end: "2026-10-13")) == nil)
        let venues = [NoticeVenue(phase: "final", name: "본관", address: "주소1", coordinates: NoticeCoordinates(latitude: 0, longitude: 0)),
                      NoticeVenue(phase: "preliminary", name: "다른 단계", address: nil, coordinates: nil),
                      NoticeVenue(phase: "final", name: "별관", address: "주소2", coordinates: NoticeCoordinates(latitude: 10, longitude: 20))]
        let notice = NoticeModel(id: contest.id, title: contest.title, aiDescription: contest.aiDescription, demoVisible: contest.demoVisible,
                                    favoriteOrganizationId: contest.favoriteOrganizationId, sourceIds: contest.sourceIds,
                                    targetUser: contest.targetUser, participationCondition: contest.participationCondition, applicationInformation: contest.applicationInformation,
                                    location: NoticeLocation(summary: "본관/별관", mode: "mixed", status: "known", venues: venues),
                                    schedule: contest.schedule, benefits: contest.benefits, qualityIssues: contest.qualityIssues,
                                    categoryPath: contest.categoryPath, contexts: contest.contexts, edition: contest.edition)
        let multiple = draft(phase("offline", name: "final"), notice: notice)!
        precondition(multiple.location == "본관 · 주소1 / 별관 · 주소2")
        precondition(multiple.notes == preliminary.notes)
        precondition(!multiple.notes.contains("다른 단계") && multiple.url == nil)
        let noLeak = draft(phase(), notice: notice)!
        precondition(noLeak.location == "온라인" && !noLeak.notes.contains("본관") && !noLeak.notes.contains("다른 단계"))
        print("PASS: activity exact KST, online URLs/unknown, exact phase join, multiple venues/source-only notes, invalid dates, no invented duration")
    }

    @MainActor
    static func detail(_ notice: NoticeModel, catalog: BundleSnapshot, schedule: [NoticeSchedule]? = nil) -> NoticeModel {
        let copy = NoticeModel(id: notice.id, title: notice.title, aiDescription: notice.aiDescription, demoVisible: notice.demoVisible,
                                  favoriteOrganizationId: notice.favoriteOrganizationId, sourceIds: notice.sourceIds,
                                  targetUser: notice.targetUser, participationCondition: notice.participationCondition, applicationInformation: notice.applicationInformation,
                                  location: notice.location, schedule: schedule ?? notice.schedule, benefits: notice.benefits,
                                  qualityIssues: notice.qualityIssues, categoryPath: notice.categoryPath, contexts: notice.contexts, edition: notice.edition)
        return SnapshotNoticeSource(notices: [copy], sources: catalog.sources).fetch(id: copy.id)!
    }

}
