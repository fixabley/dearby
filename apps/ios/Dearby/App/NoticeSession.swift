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

    init(snapshot: BundleSnapshot, favorites: FavoriteOrganizations) {
        self.favorites = favorites
        notices = NoticeRepository(source: SnapshotNoticeSource(notices: snapshot.notices, sources: snapshot.sources))
        organizations = OrganizationRepository(source: SnapshotOrganizationSource(organizations: snapshot.organizations))
        snapshotDate = snapshot.snapshotAt
        compose(snapshot)
    }

    func replaceSnapshot(_ snapshot: BundleSnapshot) {
        notices.replaceSource(SnapshotNoticeSource(notices: snapshot.notices, sources: snapshot.sources))
        organizations.replaceSource(SnapshotOrganizationSource(organizations: snapshot.organizations))
        snapshotDate = snapshot.snapshotAt
        compose(snapshot)
    }

    private func compose(_ snapshot: BundleSnapshot) {
        cards = snapshot.feedIDs.map { NoticeCardViewModel(id: $0, notices: notices, organizations: organizations, favorites: favorites) }
        favoriteCards = snapshot.organizations.map { FavoriteOrganizationCardViewModel(id: $0.id, noticeIDs: snapshot.feedIDs,
            notices: notices, organizations: organizations, favorites: favorites) }
        details = Dictionary(uniqueKeysWithValues: snapshot.feedIDs.map { id in
            (id, NoticeDetailViewModel(id: id, notices: notices, organizations: organizations, favorites: favorites))
        })
    }

    func detailState(_ id: String) -> NoticeDetailState? { details[id]?.state }
    func save(_ id: String) -> SaveOrganizationResult {
        cards.first { $0.state?.id == id }?.save() ?? .unresolved
    }
}
