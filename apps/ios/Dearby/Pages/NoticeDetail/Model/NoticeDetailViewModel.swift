@MainActor
final class NoticeDetailViewModel {
    private let favorites: FavoriteOrganizations
    private let initial: NoticeDetailState?
    init(id: String, notices: NoticeRepository, organizations: OrganizationRepository, favorites: FavoriteOrganizations) throws {
        self.favorites = favorites
        guard let notice = notices.notice(id) else { initial = nil; return }
        let path = try organizations.path(to: notice.favoriteOrganizationId)
        func resolve(_ ref: NoticeContext) throws -> NoticeInstitutionState {
            NoticeInstitutionState(organizationID: ref.organizationId, role: ref.role, label: ref.label,
                basis: ref.basis, note: ref.note, organizationName: try organizations.organization(ref.organizationId)?.name)
        }
        initial = NoticeDetailState(id: notice.id, title: notice.title, aiDescription: notice.aiDescription,
            descriptionProvenance: notice.descriptionProvenance, organizationID: notice.favoriteOrganizationId,
            organizationName: path.last?.name, organizationPath: path.dropLast().map(\.name),
            organizationLinks: try notice.organizationLinks.map(resolve), contexts: try notice.contexts.map(resolve),
            categorySummary: notice.categorySummary, targetUser: notice.targetUser,
            participationCondition: notice.participationCondition, applicationSummary: notice.applicationInformation.summary,
            scheduleSummaries: notice.schedules.map { $0.period.summary }, location: notice.location,
            benefits: notice.benefits, qualityIssues: notice.qualityIssues, edition: notice.edition,
            sourceURL: notice.sourceURL, sources: notice.sources, evidence: notice.evidence, saved: false)
    }
    var state: NoticeDetailState? {
        guard var value = initial else { return nil }
        value.saved = value.organizationID.map { favorites.ids.contains($0) } ?? false
        return value
    }
}
