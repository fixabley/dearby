import Foundation

enum CalendarDraftMapper {
    static func verifiedURL(_ raw: String?) -> URL? {
        guard let raw, !raw.contains(where: { $0.isWhitespace || $0.isNewline }),
              let components = URLComponents(string: raw),
              ["http", "https"].contains(components.scheme?.lowercased() ?? ""),
              let host = components.host, !host.isEmpty, components.user == nil, components.password == nil else { return nil }
        return components.url
    }

    static func application(_ detail: NoticeModel) -> CalendarEventDraft? {
        let application = detail.applicationInformation
        guard let interval = CalendarDatePolicy.interval(startAt: application.opensAt, startOn: application.opensOn,
                                                        endAt: application.closesAt, endOn: application.closesOn,
                                                        timezone: application.timezone, allowEndOnly: true) else { return nil }
        let deadlineOnly = application.opensAt == nil && application.opensOn == nil
        let url = verifiedURL(application.url)
        return CalendarEventDraft(title: "[\(deadlineOnly ? "신청 마감" : "신청 기간")] \(detail.title)", interval: interval,
                                  location: nil, url: url, notes: verifiedURL(detail.sourceURL?.absoluteString)?.absoluteString ?? "")
    }

    static func schedule(_ schedule: NoticePhase, detail: NoticeModel) -> CalendarEventDraft? {
        let phase = schedule.period
        guard let interval = CalendarDatePolicy.interval(startAt: phase.startsAt, startOn: phase.startsOn,
                                                        endAt: phase.endsAt, endOn: phase.endsOn,
                                                        timezone: phase.timezone, allowEndOnly: false) else { return nil }
        let online = phase.mode == "online"
        let venues = schedule.locations
        let url = online ? verifiedURL(phase.onlineUrl) : nil
        let location: String
        if online {
            location = "온라인"
        } else {
            location = venues.isEmpty ? "장소 미확인" : venues.map {
                [$0.name, $0.address].compactMap { $0 }.filter { !$0.isEmpty }.joined(separator: " · ")
            }.joined(separator: " / ")
        }
        return CalendarEventDraft(title: "[\(phase.label)] \(detail.title)", interval: interval,
                                  location: location, url: url, notes: verifiedURL(detail.sourceURL?.absoluteString)?.absoluteString ?? "")
    }

}
