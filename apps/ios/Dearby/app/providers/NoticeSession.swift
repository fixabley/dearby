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

    init(manifest: SnapshotManifest, noticeSource: any NoticeRecordSource,
         organizationSource: any OrganizationSource, favorites: FavoriteOrganizations) throws {
        self.favorites = favorites
        notices = NoticeRepository(source: noticeSource)
        organizations = OrganizationRepository(source: organizationSource)
        snapshotDate = manifest.snapshotAt
        try compose(feedIDs: manifest.feedIDs, organizationIDs: manifest.organizationIDs)
    }

    /// Explicit in-memory fixture composition; production uses SwiftDataSnapshotStore.makeSession.
    convenience init(snapshot: BundleSnapshot, favorites: FavoriteOrganizations) throws {
        try self.init(manifest: SnapshotManifest(snapshot: snapshot),
            noticeSource: SnapshotNoticeSource(notices: snapshot.notices, sources: snapshot.sources),
            organizationSource: SnapshotOrganizationSource(organizations: snapshot.organizations), favorites: favorites)
    }

    private func compose(feedIDs: [String], organizationIDs: [String]) throws {
        cards = try feedIDs.map { try NoticeCardViewModel(id: $0, notices: notices, organizations: organizations, favorites: favorites) }
        favoriteCards = try organizationIDs.map { try FavoriteOrganizationCardViewModel(id: $0, noticeIDs: feedIDs,
            notices: notices, organizations: organizations, favorites: favorites) }
        details = Dictionary(uniqueKeysWithValues: try feedIDs.map { id in
            (id, try NoticeDetailViewModel(id: id, notices: notices, organizations: organizations, favorites: favorites))
        })
    }

    func detailViewModel(_ id: String) -> NoticeDetailViewModel? { details[id] }

    func detailState(_ id: String) -> NoticeDetailState? { details[id]?.state }

}
