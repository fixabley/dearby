@MainActor
final class FavoriteOrganizationCardViewModel {
    private let favorites: FavoriteOrganizations
    private let initial: FavoriteOrganizationCardState?
    init(id: String, noticeIDs: [String], notices: NoticeRepository, organizations: OrganizationRepository, favorites: FavoriteOrganizations) {
        self.favorites = favorites
        guard let organization = organizations.organization(id) else { initial = nil; return }
        let rows = noticeIDs.compactMap { id -> FavoriteNoticeRowState? in
            guard let notice = notices.notice(id), notice.favoriteOrganizationId == organization.id else { return nil }
            var seen: Set<String> = []
            let names = notice.contexts.compactMap { ref -> String? in
                guard seen.insert(ref.organizationId).inserted else { return nil }
                return organizations.organization(ref.organizationId)?.name
            }.joined(separator: " · ")
            return FavoriteNoticeRowState(id: id, title: notice.title, category: notice.categorySummary, contextNames: names)
        }
        initial = FavoriteOrganizationCardState(id: organization.id, name: organization.name,
            ancestorNames: organizations.path(to: id).dropLast().map(\.name).joined(separator: " › "), notices: rows)
    }
    var state: FavoriteOrganizationCardState? {
        guard let value = initial, favorites.ids.contains(value.id) else { return nil }
        return value
    }
    func remove() { if let id = initial?.id { favorites.remove(id) } }
}
