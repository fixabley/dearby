/// App composition constructs dependencies and page destinations without implementing feature UI.
@MainActor
extension AppSession {
    convenience init() {
        self.init(snapshotReader: BundleSnapshotReader(),
            favorites: FavoriteOrganizations(repository: UserDefaultsFavoriteOrganizationsRepository()),
            calendarPreferences: CalendarPreferences(store: BusyCalendarProviderFactory.makePreferenceStore(), provider: BusyCalendarProviderFactory.make()))
    }

    func detailPage(_ id: String) -> NoticeDetailPage? {
        guard let viewModel = catalog?.detailViewModel(id) else { return nil }
        return NoticeDetailPage(viewModel: viewModel, preferences: calendarPreferences)
    }

}
