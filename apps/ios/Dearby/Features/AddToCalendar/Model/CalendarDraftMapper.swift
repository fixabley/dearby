import Foundation

enum CalendarDraftMapper {
    static func verifiedURL(_ raw: String?) -> URL? {
        guard let raw, !raw.contains(where: { $0.isWhitespace || $0.isNewline }),
              let components = URLComponents(string: raw),
              ["http", "https"].contains(components.scheme?.lowercased() ?? ""),
              let host = components.host, !host.isEmpty, components.user == nil, components.password == nil else { return nil }
        return components.url
    }

    static func application(_ detail: NoticeDetail) -> CalendarEventDraft? {
        let application = detail.applicationInformation
        guard let interval = CalendarDatePolicy.interval(startAt: application.opensAt, startOn: application.opensOn,
                                                        endAt: application.closesAt, endOn: application.closesOn,
                                                        timezone: application.timezone, allowEndOnly: true) else { return nil }
        let deadlineOnly = application.opensAt == nil && application.opensOn == nil
        let url = verifiedURL(application.url)
        var notes = [application.summary, "시간대: \(application.timezone ?? "Asia/Seoul")",
                     "신청 시작: \(application.opensAt ?? application.opensOn ?? "미확인")",
                     "신청 마감: \(application.closesAt ?? application.closesOn ?? "미확인")"]
        notes.append(url.map { "신청 URL: \($0.absoluteString)" } ?? "신청 URL: 미확인")
        if let source = verifiedURL(detail.sourceURL?.absoluteString) { notes.append("원문: \(source.absoluteString)") }
        return CalendarEventDraft(title: "[\(deadlineOnly ? "신청 마감" : "신청 기간")] \(detail.title)", interval: interval,
                                  location: nil, url: url, notes: notes.joined(separator: "\n"))
    }

    static func schedule(_ schedule: NoticeDetailSchedule, detail: NoticeDetail,
                         mapURL: (NoticeVenue) -> URL?) -> CalendarEventDraft? {
        let phase = schedule.period
        guard let interval = CalendarDatePolicy.interval(startAt: phase.startsAt, startOn: phase.startsOn,
                                                        endAt: phase.endsAt, endOn: phase.endsOn,
                                                        timezone: phase.timezone, allowEndOnly: false) else { return nil }
        let online = phase.mode == "online"
        let venues = schedule.locations
        let url = online ? verifiedURL(phase.onlineUrl) : nil
        let location: String
        var notes = [phase.summary, "시간대: \(phase.timezone ?? "Asia/Seoul")",
                     "활동 시작: \(phase.startsAt ?? phase.startsOn ?? "미확인")",
                     "활동 종료: \(phase.endsAt ?? phase.endsOn ?? "미확인")"]
        if online {
            location = "온라인"
            notes.append(url.map { "온라인 URL: \($0.absoluteString)" } ?? "온라인 · 접속 URL 미확인")
        } else {
            location = venues.isEmpty ? "장소 미확인" : venues.map {
                [$0.name, $0.address].compactMap { $0 }.filter { !$0.isEmpty }.joined(separator: " · ")
            }.joined(separator: " / ")
            if !venues.isEmpty { notes.append("장소 안내: \(schedule.locationSummary)") }
            for venue in venues {
                if let link = mapURL(venue) { notes.append("지도 (\(venue.name)): \(link.absoluteString)") }
            }
        }
        if let source = verifiedURL(detail.sourceURL?.absoluteString) { notes.append("원문: \(source.absoluteString)") }
        return CalendarEventDraft(title: "[\(phase.label)] \(detail.title)", interval: interval,
                                  location: location, url: url, notes: notes.joined(separator: "\n"))
    }

}
