import Foundation

@MainActor
final class NoticeCardViewModel: Identifiable {
    let id: String
    private let notice: NoticeModel?
    private let favorites: FavoriteOrganizations
    private let organization: OrganizationModel?
    private let initial: NoticeCardState?
    init(id: String, notices: NoticeRepository, organizations: OrganizationRepository, favorites: FavoriteOrganizations) throws {
        self.id = id
        self.favorites = favorites
        notice = try notices.notice(id)
        guard let notice else { organization = nil; initial = nil; return }
        organization = try organizations.organization(notice.favoriteOrganizationId)
        var seen: Set<String> = []
        let names = try notice.contexts.compactMap { ref -> String? in
            guard seen.insert(ref.organizationId).inserted else { return nil }
            return try organizations.organization(ref.organizationId)?.name
        }.joined(separator: " · ")
        initial = NoticeCardState(id: notice.id, title: notice.title, category: notice.categorySummary,
            contextNames: names, targetUser: notice.targetUser,
            hasQualityIssues: !notice.qualityIssues.isEmpty,
            organizationName: organization?.name, saved: false,
            schedules: NoticeCardScheduleState.project(notice))
    }
    /// Feed membership is snapshot-stable and never observes saved IDs.
    var isDisplayable: Bool { initial != nil }

    var state: NoticeCardState? {
        guard var result = initial else { return nil }
        result.saved = organization.map { favorites.ids.contains($0.id) } ?? false
        return result
    }
    func venue(scheduleIndex: Int, venueIndex: Int) -> NoticeVenue? {
        guard let notice, notice.schedules.indices.contains(scheduleIndex),
              notice.schedules[scheduleIndex].locations.indices.contains(venueIndex) else { return nil }
        return notice.schedules[scheduleIndex].locations[venueIndex]
    }
    func save() -> SaveOrganizationResult { favorites.saveOrganization(organization) }
}
