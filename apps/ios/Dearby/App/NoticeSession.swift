import Observation

/// App-scoped snapshot owner. ViewModels contain resolved rendering values, not IO in body.
@MainActor
@Observable
final class NoticeSession {
    let notices: NoticeRepository
    let organizations: OrganizationRepository
    private let favorites: FavoriteOrganizations
    private(set) var snapshotDate: String
    private(set) var cards: [NoticeCardViewModel] = []
    private(set) var favoriteCards: [FavoriteOrganizationCardViewModel] = []
    @ObservationIgnored private var details: [String: NoticeDetailViewModel] = [:]

    init(snapshot: BundleSnapshot, favorites: FavoriteOrganizations) throws {
        self.favorites = favorites
        notices = NoticeRepository(source: SnapshotNoticeSource(notices: snapshot.notices, sources: snapshot.sources))
        organizations = OrganizationRepository(source: SnapshotOrganizationSource(organizations: snapshot.organizations))
        snapshotDate = snapshot.snapshotAt
        try compose(snapshot)
    }

    func replaceSnapshot(_ snapshot: BundleSnapshot) throws {
        notices.replaceSource(SnapshotNoticeSource(notices: snapshot.notices, sources: snapshot.sources))
        organizations.replaceSource(SnapshotOrganizationSource(organizations: snapshot.organizations))
        snapshotDate = snapshot.snapshotAt
        try compose(snapshot)
    }

    private func compose(_ snapshot: BundleSnapshot) throws {
        cards = try snapshot.feedIDs.map { try NoticeCardViewModel(id: $0, notices: notices, organizations: organizations, favorites: favorites) }
        favoriteCards = try snapshot.organizations.map { try FavoriteOrganizationCardViewModel(id: $0.id, noticeIDs: snapshot.feedIDs,
            notices: notices, organizations: organizations, favorites: favorites) }
        details = Dictionary(uniqueKeysWithValues: try snapshot.feedIDs.map { id in
            (id, try NoticeDetailViewModel(id: id, notices: notices, organizations: organizations, favorites: favorites))
        })
    }

    func detailState(_ id: String) -> NoticeDetailState? { details[id]?.state }
    func save(_ id: String) -> SaveOrganizationResult {
        cards.first { $0.state?.id == id }?.save() ?? .unresolved
    }
}
