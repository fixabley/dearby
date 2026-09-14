@MainActor
final class NoticeCardViewModel {
    private let favorites: FavoriteOrganizations
    private let organization: OrganizationModel?
    private let initial: NoticeCardState?
    init(id: String, notices: NoticeRepository, organizations: OrganizationRepository, favorites: FavoriteOrganizations) throws {
        self.favorites = favorites
        guard let notice = notices.notice(id) else { organization = nil; initial = nil; return }
        organization = try organizations.organization(notice.favoriteOrganizationId)
        var seen: Set<String> = []
        let names = try notice.contexts.compactMap { ref -> String? in
            guard seen.insert(ref.organizationId).inserted else { return nil }
            return try organizations.organization(ref.organizationId)?.name
        }.joined(separator: " · ")
        initial = NoticeCardState(id: notice.id, title: notice.title, category: notice.categorySummary,
            contextNames: names, targetUser: notice.targetUser, applicationSummary: notice.applicationInformation.summary,
            locationSummary: notice.location.summary, hasQualityIssues: !notice.qualityIssues.isEmpty,
            organizationName: organization?.name, saved: false)
    }
    var state: NoticeCardState? {
        guard var result = initial else { return nil }
        result.saved = organization.map { favorites.ids.contains($0.id) } ?? false
        return result
    }
    func save() -> SaveOrganizationResult { favorites.saveOrganization(organization) }
}
