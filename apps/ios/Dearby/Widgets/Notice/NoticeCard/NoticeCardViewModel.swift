import Foundation

@MainActor
final class NoticeCardViewModel {
    private let favorites: FavoriteOrganizations
    private let organization: OrganizationModel?
    private let initial: NoticeCardState?
    init(id: String, notices: NoticeRepository, organizations: OrganizationRepository, favorites: FavoriteOrganizations) throws {
        self.favorites = favorites
        guard let notice = try notices.notice(id) else { organization = nil; initial = nil; return }
        organization = try organizations.organization(notice.favoriteOrganizationId)
        var seen: Set<String> = []
        let names = try notice.contexts.compactMap { ref -> String? in
            guard seen.insert(ref.organizationId).inserted else { return nil }
            return try organizations.organization(ref.organizationId)?.name
        }.joined(separator: " · ")
        initial = NoticeCardState(id: notice.id, title: notice.title, category: notice.categorySummary,
            contextNames: names, targetUser: notice.targetUser, applicationSummary: notice.applicationInformation.summary,
            locationSummary: notice.location.summary, hasQualityIssues: !notice.qualityIssues.isEmpty,
            organizationName: organization?.name, saved: false,
            schedules: Self.scheduleStates(notice),
            applicationPeriod: CompactPeriod.period(start: notice.applicationInformation.opensAt ?? notice.applicationInformation.opensOn,
                end: notice.applicationInformation.closesAt ?? notice.applicationInformation.closesOn,
                timezone: notice.applicationInformation.timezone, fallback: notice.applicationInformation.summary))
    }
    var state: NoticeCardState? {
        guard var result = initial else { return nil }
        result.saved = organization.map { favorites.ids.contains($0.id) } ?? false
        return result
    }
    func save() -> SaveOrganizationResult { favorites.saveOrganization(organization) }
}

extension NoticeCardViewModel {
    static func scheduleStates(_ notice: NoticeModel) -> [NoticeCardScheduleState] {
        let application = notice.applicationInformation
        let destinations = (application.submissionLocations ?? []) + [application.url].compactMap { $0 }
        let applicationPlaces = destinations.filter { !$0.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty }
        let first = NoticeCardScheduleState(id: -1, title: "신청 기간",
            period: period(start: application.opensAt ?? application.opensOn, end: application.closesAt ?? application.closesOn,
                           timezone: application.timezone, fallback: application.summary),
            places: [.init(id: 0, fields: applicationPlaces.isEmpty ? ["신청 위치 미확인"] : applicationPlaces.map(placeLabel), venueIndex: nil)])
        return [first] + notice.schedules.enumerated().map { index, phase in
            let schedule = phase.period
            var places: [NoticeCardPlaceState] = []
            if schedule.mode == "online" || schedule.mode == "hybrid" || schedule.onlineUrl != nil {
                let url = schedule.onlineUrl?.trimmingCharacters(in: .whitespacesAndNewlines)
                places.append(.init(id: -1, fields: ["온라인", (url?.isEmpty == false) ? placeLabel(url!) : "URL 미확인"], venueIndex: nil))
            }
            if schedule.mode != "online" {
                places += phase.locations.enumerated().map { venueIndex, venue in
                    .init(id: venueIndex, fields: [venue.name, venue.address].compactMap { $0 }.filter { !$0.isEmpty },
                          venueIndex: venue.coordinates == nil ? nil : venueIndex)
                }
            }
            if places.isEmpty { places = [.init(id: 0, fields: ["장소 미확인"], venueIndex: nil)] }
            return .init(id: index, title: schedule.label,
                period: period(start: schedule.startsAt ?? schedule.startsOn, end: schedule.endsAt ?? schedule.endsOn,
                               timezone: schedule.timezone, fallback: "일정 미확인"), places: places)
        }
    }

    // Only shorten a whole web URL; addresses and prose containing links stay intact.
    static func placeLabel(_ text: String) -> String {
        let candidate = text.trimmingCharacters(in: .whitespacesAndNewlines)
        guard candidate.rangeOfCharacter(from: .whitespacesAndNewlines) == nil,
              let url = URL(string: candidate),
              ["http", "https"].contains(url.scheme?.lowercased() ?? ""),
              let host = url.host, !host.isEmpty else { return text }
        return host
    }

    static func period(start: String?, end: String?, timezone: String?, fallback: String) -> [String] {
        let zone = timezone.flatMap(TimeZone.init(identifier:))
        let zoneSuffix = zone == nil || timezone == "Asia/Seoul" ? "" : " (\(timezone!))"
        func point(_ value: String) -> String {
            var text = CompactPeriod.period(start: value, end: value, timezone: timezone, fallback: fallback)
            if zone != nil, let timezone {
                let suffix = timezone == "Asia/Seoul" ? " (한국 시간)" : " (\(timezone))"
                if text.hasSuffix(suffix) { text.removeLast(suffix.count) }
            }
            return text
        }
        switch (start, end) {
        case let (start?, end?):
            let check = CompactPeriod.period(start: start, end: end, timezone: timezone, fallback: fallback)
            if check.contains("기간 순서 확인 필요") { return [start + "부터", end + "까지", "기간 순서 확인 필요"] }
            return ["\(point(start))부터", "\(point(end))까지"] + (zoneSuffix.isEmpty ? [] : [zoneSuffix.trimmingCharacters(in: .whitespaces)])
        case let (start?, nil): return ["\(point(start))부터", "종료 미확인" + zoneSuffix]
        case let (nil, end?): return ["시작 미확인", "\(point(end))까지" + zoneSuffix]
        default: return [fallback]
        }
    }
}
