@MainActor
extension AppState {
    /// Candidate repositories and display models stay private until the snapshot transaction commits.
    static func composeSnapshot(_ snapshot: BundleSnapshot, storage: SwiftDataSnapshotStore, favorites: FavoriteOrganizations,
                                organizationSource: any OrganizationSource) throws
        -> (NoticeRepository, OrganizationRepository, [NoticeCardViewModel], [FavoriteOrganizationCardViewModel], String) {
        try storage.withSnapshot(snapshot) { metadata in
            let notices = NoticeRepository(source: SwiftDataNoticeSource(context: storage.context,
                external: SnapshotNoticeSource(notices: snapshot.notices, sources: snapshot.sources), commit: storage.persistCache))
            let organizations = OrganizationRepository(source: SwiftDataOrganizationSource(context: storage.context,
                external: organizationSource, commit: storage.persistCache))
            let cards = try metadata.feedIDs.map {
                try NoticeCardViewModel(id: $0, notices: notices, organizations: organizations, favorites: favorites)
            }
            let favoriteCards = try metadata.organizationIDs.map {
                try FavoriteOrganizationCardViewModel(id: $0, noticeIDs: metadata.feedIDs,
                    notices: notices, organizations: organizations, favorites: favorites)
            }
            return (notices, organizations, cards, favoriteCards, metadata.snapshotAt)
        }
    }
}
