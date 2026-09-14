import Foundation

enum CalendarDraftMapper {
    static func verifiedURL(_ raw: String?) -> URL? {
        guard let raw, !raw.contains(where: { $0.isWhitespace || $0.isNewline }),
              let components = URLComponents(string: raw),
              ["http", "https"].contains(components.scheme?.lowercased() ?? ""),
              let host = components.host, !host.isEmpty, components.user == nil, components.password == nil else { return nil }
        return components.url
    }

    static func application(_ notice: ActivityNotice, catalog: ActivityCatalog) -> CalendarEventDraft? {
        let application = notice.application
        guard let interval = CalendarDatePolicy.interval(startAt: application.opensAt, startOn: application.opensOn,
                                                        endAt: application.closesAt, endOn: application.closesOn,
                                                        timezone: application.timezone, allowEndOnly: true) else { return nil }
        let deadlineOnly = application.opensAt == nil && application.opensOn == nil
        let url = verifiedURL(application.url)
        var notes = [application.summary, "시간대: \(application.timezone ?? "Asia/Seoul")",
                     "신청 시작: \(application.opensAt ?? application.opensOn ?? "미확인")",
                     "신청 마감: \(application.closesAt ?? application.closesOn ?? "미확인")"]
        notes.append(url.map { "신청 URL: \($0.absoluteString)" } ?? "신청 URL: 미확인")
        if let source = verifiedURL(catalog.sourceURL(for: notice)?.absoluteString) { notes.append("원문: \(source.absoluteString)") }
        return CalendarEventDraft(title: "[\(deadlineOnly ? "신청 마감" : "신청 기간")] \(notice.title)", interval: interval,
                                  location: nil, url: url, notes: notes.joined(separator: "\n"))
    }
}
